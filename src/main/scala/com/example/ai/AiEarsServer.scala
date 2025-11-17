package com.example.ai

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.io.StdIn

final case class AiCommand(eqpId: String, scenario: String)
final case class AiResponse(status: String, message: String)

trait JsonSupport extends DefaultJsonProtocol {
  implicit val commandFormat: RootJsonFormat[AiCommand] = jsonFormat2(AiCommand)
  implicit val responseFormat: RootJsonFormat[AiResponse] = jsonFormat2(AiResponse)
}

object AiEarsServer extends App with JsonSupport {
  implicit val system: ActorSystem = ActorSystem("ai-ears-system")
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  implicit val materializer: Materializer = Materializer(system)

  private val route: Route =
    path("ai_ears") {
      post {
        entity(as[AiCommand]) { payload =>
          val acknowledgement = AiResponse(
            status = "accepted",
            message = s"Request for ${payload.eqpId} and scenario ${payload.scenario} received."
          )
          complete(StatusCodes.Accepted -> acknowledgement)
        }
      } ~
        complete(
          HttpResponse(
            status = StatusCodes.MethodNotAllowed,
            entity = "Only POST is supported on /ai_ears"
          )
        )
    }

  private val bindingFuture: Future[Http.ServerBinding] =
    Http().newServerAt("0.0.0.0", 8080).bind(route)

  println("AI EARS server online at http://0.0.0.0:8080/ai_ears\nPress ENTER to stop.")
  StdIn.readLine()

  bindingFuture
    .flatMap(_.terminate(hardDeadline = 5.seconds))
    .onComplete(_ => system.terminate())
}
