package lovegress

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.{RequestContext, Route}
import lovegress.IngressController.ServiceIpsWithPort
import lovegress.Main.{complete, extractRequestContext, onComplete}

import scala.util.{Failure, Random, Success}

class Router(controller: IngressController)(implicit as: ActorSystem) {

  val route: Route = extractRequestContext { context =>
    val host = context.request.uri.authority.host.toString()
    println(s"Processing request for the host '$host'")

    ipAndPortForHost(host)
      .map { case (ip, port) =>
        routeRequest(context, ip, port)
      }
      .getOrElse {
        println(s"The host is unknown")
        complete(StatusCodes.ServiceUnavailable)
      }
  }

  private def routeRequest(context: RequestContext, ip: String, port: Int): Route = {
    val newUri = context.request.uri.toRelative.toEffectiveHttpRequestUri(Uri.Host(ip), port)
    val newRequest = context.request.copy(uri = newUri)
    println(s"Updated request is $newRequest")
    onComplete(Http().singleRequest(newRequest)) {
      case Success(response) =>
        println(s"The result is success: $response")
        complete(response)
      case Failure(exception) =>
        println(s"The result is error: $exception")
        complete(StatusCodes.BadGateway)
    }
  }

  private def ipAndPortForHost(host: String): Option[(String, Int)] = {
    val ServiceIpsWithPort(_, ips, port) = controller.getIpsWithPort(host)
    if (ips.isEmpty) None
    else {
      val randomIp = Random.shuffle(ips).head
      Some(randomIp, port)
    }
  }

}
