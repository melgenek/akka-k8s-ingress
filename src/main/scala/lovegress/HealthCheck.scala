package lovegress

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives

object HealthCheck extends Directives {

  val route = get {
    path("health") {
      complete(StatusCodes.OK)
    }
  }

}
