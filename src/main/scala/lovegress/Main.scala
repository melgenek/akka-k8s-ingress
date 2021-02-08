package lovegress

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives

import scala.util.{Failure, Success}

object Main extends App with Directives {
  implicit val system = ActorSystem()
  implicit val ec = system.dispatcher

  val k8sClient = new KubernetesClient
  val controller = new IngressController(k8sClient)
  val router = new Router(controller)

  val routes = HealthCheck.route ~ router.route

  (for {
    _ <- controller.init()
    binding <- Http().bindAndHandle(routes, interface = "0.0.0.0", port = 8080)
  } yield binding).onComplete {
    case Success(binding) =>
      println(s"Started on ${binding.localAddress}")
    case Failure(exception) =>
      println(s"Failed to start")
      exception.printStackTrace()
  }
}
