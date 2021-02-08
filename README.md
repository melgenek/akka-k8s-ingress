
kubectl apply -f kuard
minikube addons enable ingress

minikube ip
minikube service kuard

kubectl proxy --disable-filter --v=10

kubectl run curl -it --image=radial/busyboxplus:curl 

docker system prune 

eval $(minikube docker-env)
