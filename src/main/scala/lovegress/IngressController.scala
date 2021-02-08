package lovegress

import java.util.concurrent.atomic.AtomicReference

import akka.actor.ActorSystem
import lovegress.IngressController.ServiceIpsWithPort

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class IngressController(k8sClient: KubernetesClient)
                       (implicit as: ActorSystem, ec: ExecutionContext) {

  private val hostToIps: AtomicReference[Map[String, ServiceIpsWithPort]] = new AtomicReference(Map.empty)

  def getIpsWithPort(host: String): ServiceIpsWithPort = {
    hostToIps.get().getOrElse(host, ServiceIpsWithPort("unknown", Seq.empty, -1))
  }

  def init(): Future[Unit] = {
    updateHostsToIpsMappings()
      .andThen {
        case Success(_) =>
          watchIngressUpdates()
          watchEndpointUpdates()
      }
  }

  private def watchIngressUpdates(): Unit = {
    k8sClient
      .watchIngresses()
      .map(_.`object`)
      .runForeach(_ => updateHostsToIpsMappings())
      .onComplete(result => println(s"Ingresses are not being watched any more. Result: $result"))
    ()
  }

  private def watchEndpointUpdates(): Unit = {
    k8sClient
      .watchEndpoints()
      .map(_.`object`)
      .filter(endpoint => isServiceKnown(endpoint.metadata.name))
      .runForeach(_ => updateHostsToIpsMappings())
      .onComplete(result => println(s"Endpoints are not being watched any more. Result: $result"))
    ()
  }

  private def isServiceKnown(service: String): Boolean = {
    hostToIps.get.values.exists(_.service == service)
  }

  private def updateHostsToIpsMappings(): Future[Unit] = {
    val ingressesFuture = k8sClient.ingresses()
    val endpointsFuture = k8sClient.endpoints()
    for {
      ingresses <- ingressesFuture
      endpoints <- endpointsFuture
    } yield {
      val newMappings = buildHostsToIpsMappings(ingresses.items.toSeq, endpoints.items.toSeq)
      hostToIps.set(newMappings)
      println(s"Mappings are updated: $newMappings")
    }
  }

  private def buildHostsToIpsMappings(ingresses: Seq[Ingress], endpoints: Seq[Endpoint]): Map[String, ServiceIpsWithPort] = {
    val endpointsToIps = endpoints
      .collect {
        case Endpoint(ResourceMetadata(name, _, _), Some(endpointSubsets)) =>
          val ips = endpointSubsets.flatMap(_.addresses.toSeq.flatten).map(_.ip)
          (name, ips)
      }
      .toMap

    val hostsToIps = ingresses
      .filter(shouldProcessIngress)
      .flatMap {
        _.spec.rules.map { case IngressRule(host, ingressRuleValue) =>
          val pathBackend = ingressRuleValue.paths.head.backend
          val ips = endpointsToIps.getOrElse(pathBackend.serviceName, Seq.empty)
          (host, ServiceIpsWithPort(pathBackend.serviceName, ips, pathBackend.servicePort))
        }
      }
      .toMap

    hostsToIps
  }

  private def shouldProcessIngress(ingress: Ingress): Boolean = {
    ingress.metadata.annotations.exists { annotations =>
      annotations.get("kubernetes.io/ingress.class").contains("lovegress")
    }
  }

}

object IngressController {
  case class ServiceIpsWithPort(service: String, ips: Seq[String], port: Int)
}
