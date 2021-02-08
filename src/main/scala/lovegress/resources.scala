package lovegress

case class ResourceList[T](kind: String,
                           apiVersion: String,
                           items: Seq[T])

case class ResourceUpdate[T](`type`: String, `object`: T)

case class ResourceMetadata(name: String, namespace: String, annotations: Option[Map[String, String]])

case class Ingress(metadata: ResourceMetadata, spec: IngressSpec)
case class IngressSpec(rules: Seq[IngressRule])
case class IngressRule(host: String, http: HttpIngressRuleValue)
case class HttpIngressRuleValue(paths: Seq[HttpIngressPath])
case class HttpIngressPath(backend: IngressBackend)
case class IngressBackend(serviceName: String, servicePort: Int)

case class Endpoint(metadata: ResourceMetadata, subsets: Option[Seq[EndpointSubsets]])
case class EndpointSubsets(addresses: Option[Seq[EndpointAddress]], ports: Seq[EndpointPort])
case class EndpointAddress(ip: String)
case class EndpointPort(port: Int)

