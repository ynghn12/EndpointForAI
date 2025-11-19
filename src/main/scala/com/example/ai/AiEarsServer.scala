package com.example.ai

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import org.slf4j.LoggerFactory
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.io.StdIn
import scala.util.{Failure, Success}
import scala.util.control.NonFatal

final case class AiCommand(eqpId: String, scenario: String)
final case class ErrorResponse(code: String, message: String)
final case class ASystemCommand(equipmentId: String, scenario: String)

trait JsonSupport extends DefaultJsonProtocol {
  implicit val commandFormat: RootJsonFormat[AiCommand] = jsonFormat2(AiCommand)
  implicit val errorFormat: RootJsonFormat[ErrorResponse] = jsonFormat2(ErrorResponse)
  implicit val aSystemFormat: RootJsonFormat[ASystemCommand] = jsonFormat2(ASystemCommand)
}

object AiEarsServer extends App with JsonSupport {
  implicit val system: ActorSystem = ActorSystem("ai-ears-system")
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  implicit val materializer: Materializer = Materializer(system)

  private val logger = LoggerFactory.getLogger(getClass)
  private val httpStrictTimeout: FiniteDuration = 5.seconds
  // Downstream URL is injected for deployments; keep a noisy fallback for local smoke tests.
  private val aSystemUri: Uri = sys.env
    .get("A_SYSTEM_URL")
    .map(Uri(_))
    .getOrElse {
      val fallback = "http://127.0.0.1:8090/a-system"
      logger.warn(s"A_SYSTEM_URL not configured, falling back to $fallback")
      Uri(fallback)
    }

  private val UnsupportedMediaTypeResponse =
    ErrorResponse(
      code = "UNSUPPORTED_MEDIA_TYPE",
      message = "Content-Type must be application/json."
    )

  private val route: Route =
    path("ai_ears") {
      post {
        extractRequest { request =>
          val requestId = extractRequestId(request)
          if (!isJsonContentType(request.entity.contentType)) {
            complete(StatusCodes.UnsupportedMediaType -> UnsupportedMediaTypeResponse)
          } else {
            entity(as[AiCommand]) { payload =>
              val normalized = sanitize(payload)
              validateCommand(normalized) match {
                case Some(errorMessage) =>
                  val error = ErrorResponse("INVALID_PAYLOAD", errorMessage)
                  complete(StatusCodes.BadRequest -> error)
                case None =>
                  onComplete(forwardToASystem(normalized, requestId)) {
                    case Success(response) =>
                      complete(response)
                    case Failure(_) =>
                      val error = ErrorResponse(
                        code = "A_SYSTEM_UNAVAILABLE",
                        message = "A 시스템 호출 중 오류가 발생했습니다."
                      )
                      complete(StatusCodes.BadGateway -> error)
                  }
              }
            }
          }
        }
      } ~
        complete(
          HttpResponse(
            status = StatusCodes.MethodNotAllowed,
            entity = "Only POST is supported on /ai_ears"
          )
        )
    }

  // Forward validated payloads to A 시스템 and surface the raw response upstream.
  private def forwardToASystem(command: AiCommand, requestId: Option[String]): Future[HttpResponse] = {
    val entity = HttpEntity(
      ContentTypes.`application/json`,
      ASystemCommand(command.eqpId, command.scenario).toJson.compactPrint
    )

    val downstreamHeaders = requestId.map(id => RawHeader("X-Request-Id", id)).toList
    val httpRequest =
      HttpRequest(method = HttpMethods.POST, uri = aSystemUri, entity = entity).withHeaders(downstreamHeaders: _*)
    val start = System.nanoTime()

    Http()
      .singleRequest(httpRequest)
      .flatMap { response =>
        response.entity.toStrict(httpStrictTimeout).map { strictEntity =>
          val latencyMs = (System.nanoTime() - start) / 1e6
          val logPrefix =
            s"eqpId=${command.eqpId}, scenario=${command.scenario}, status=${response.status.intValue()}, latencyMs=$latencyMs, requestId=${requestId.getOrElse("n/a")}"
          val updatedResponse = response.withEntity(strictEntity)

          if (isServerError(response.status)) {
            val body = limitForLog(strictEntity.data.utf8String)
            logger.error(s"A 시스템 5xx 응답 - $logPrefix, body=$body")
          } else if (response.status.isSuccess()) {
            logger.info(s"A 시스템 호출 성공 - $logPrefix")
          } else {
            logger.warn(s"A 시스템 비정상 응답 - $logPrefix")
          }

          updatedResponse
        }
      }
      .recoverWith { case NonFatal(ex) =>
        logger.error(
          s"A 시스템 호출 중 예외(eqpid=${command.eqpId}, scenario=${command.scenario}, requestId=${requestId.getOrElse("n/a")})",
          ex
        )
        Future.failed(ex)
      }
  }

  private def isJsonContentType(contentType: ContentType): Boolean =
    contentType.mediaType.isApplication && contentType.mediaType.subType == "json"

  private def extractRequestId(request: HttpRequest): Option[String] =
    request.headers.collectFirst {
      case header if header.is("x-request-id") => header.value()
    }

  private def sanitize(command: AiCommand): AiCommand =
    command.copy(eqpId = command.eqpId.trim, scenario = command.scenario.trim)

  private def validateCommand(command: AiCommand): Option[String] = {
    val errors = List(
      Option.when(command.eqpId.isEmpty)("eqpId must not be blank"),
      Option.when(command.scenario.isEmpty)("scenario must not be blank")
    ).flatten

    Option.when(errors.nonEmpty)(errors.mkString(", "))
  }

  private def limitForLog(value: String, limit: Int = 2048): String =
    if (value.length <= limit) value else value.take(limit) + "..."

  private def isServerError(statusCode: StatusCode): Boolean = {
    val code = statusCode.intValue()
    code >= 500 && code < 600
  }

  private val bindingFuture: Future[Http.ServerBinding] =
    Http().newServerAt("0.0.0.0", 8080).bind(route)

  println("AI EARS server online at http://0.0.0.0:8080/ai_ears\nPress ENTER to stop.")
  StdIn.readLine()

  bindingFuture
    .flatMap(_.terminate(hardDeadline = 5.seconds))
    .onComplete(_ => system.terminate())
}
