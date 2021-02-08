package lovegress

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.{Authorization, HttpOrigin, OAuth2BearerToken, Origin}
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl._
import io.circe.Decoder
import io.circe.generic.auto._
import io.circe.parser.decode
import lovegress.KubernetesClient.{EndpointsPath, IngressesPath}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class KubernetesClient(implicit as: ActorSystem, ec: ExecutionContext) {

  private val baseHttpUri =
    if (sys.env.contains("TEST_MODE")) "http://localhost:8001"
    else "https://kubernetes.default.svc"

  private val baseWsUri =
    if (sys.env.contains("TEST_MODE")) "ws://localhost:8001"
    else "wss://kubernetes.default.svc"

  private val authorizationHeader: Option[Authorization] = Try {
    Authorization(OAuth2BearerToken(
      scala.io.Source.fromFile("/var/run/secrets/kubernetes.io/serviceaccount/token").getLines.mkString("\n")
    ))
  }.toOption


  def ingresses(): Future[ResourceList[Ingress]] = {
    resources[Ingress](IngressesPath)
  }

  def endpoints(): Future[ResourceList[Endpoint]] = {
    resources[Endpoint](EndpointsPath)
  }

  private def resources[T: Decoder](path: String): Future[ResourceList[T]] = {
    for {
      response <- Http().singleRequest(HttpRequest(uri = s"$baseHttpUri/$path", headers = authorizationHeader.toSeq))
      rawResponseBody <- Unmarshal(response).to[String]
    } yield decodeResponse[ResourceList[T]](rawResponseBody)
  }

  def watchIngresses(): Source[ResourceUpdate[Ingress], NotUsed] = {
    watchResource[Ingress](IngressesPath)
  }

  def watchEndpoints(): Source[ResourceUpdate[Endpoint], NotUsed] = {
    watchResource[Endpoint](EndpointsPath)
  }

  def watchResource[T: Decoder](path: String): Source[ResourceUpdate[T], NotUsed] = {
    val request = WebSocketRequest(s"$baseWsUri/$path?watch=true",
      Seq(Origin(HttpOrigin(baseWsUri))) ++ authorizationHeader.toSeq
    )

    val (upgradeResponse, updates) = Source.maybe[Message]
      .viaMat(Http().webSocketClientFlow(request))(Keep.right)
      .toMat(Sink.asPublisher[Message](fanout = false).mapMaterializedValue(Source.fromPublisher))(Keep.both)
      .run()

    Source.future(upgradeResponse)
      .collect {
        case InvalidUpgradeResponse(_, cause) =>
          throw new IllegalStateException(s"Websocket connection is NOT established. Cause: $cause")
      }
      .concat(updates)
      .collect {
        case message: TextMessage.Strict => decodeResponse[ResourceUpdate[T]](message.text)
      }
  }

  private def decodeResponse[T: Decoder](rawResponseBody: String): T = {
    decode[T](rawResponseBody).getOrElse(
      throw new IllegalArgumentException(s"The response cannot be decoded. '$rawResponseBody'")
    )
  }

}

object KubernetesClient {

  private val EndpointsPath = "api/v1/endpoints"
  private val IngressesPath = "apis/networking.k8s.io/v1beta1/ingresses"

}
