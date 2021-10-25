package io.orite

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.alpakka.sqs.scaladsl.{SqsAckFlow, SqsAckSink, SqsSource}
import akka.stream.alpakka.sqs.{MessageAction, SqsSourceSettings}
import akka.stream.scaladsl.{Flow, Keep, RestartSource, Sink, Source}
import akka.stream._
import com.typesafe.scalalogging.LazyLogging
import io.circe
import io.orite.model.Person
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, AwsCredentialsProvider, StaticCredentialsProvider}
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.core.internal.retry.SdkDefaultRetrySetting
import software.amazon.awssdk.core.retry.RetryPolicy
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy
import software.amazon.awssdk.core.retry.conditions.RetryCondition
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.Message

import java.net.URI
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.control.NonFatal

object Application extends App with LazyLogging {

  private[this] val AWS_ACCESS_KEY_ID = "local"
  private[this] val AWS_SECRET_KEY_ID = "local"
  private[this] val SQS_QUEUE_URL = "http://localhost:4566/000000000000/leads-incoming-queue"
  private[this] val SECURITY_TOKEN_URL = "http://localhost:4566"

  implicit val actorSystem: ActorSystem = ActorSystem("test-actor-system")

  implicit val materializer: Materializer = ActorMaterializer(
    ActorMaterializerSettings(actorSystem).withSupervisionStrategy { error =>
      logger.error("Stream processing has failed", error)
      error match {
        case NonFatal(err) =>
          logger error ("Encountered non-fatal error so dropping the element from the stream!", err)
          Supervision.Resume
        case _ =>
          logger info s"Encountered fatal error so, restarting ..."
          Supervision.Restart
      }
    }
  )

  private[this] val clientOverrideConfiguration: ClientOverrideConfiguration = {
    ClientOverrideConfiguration
      .builder()
      .retryPolicy(
        RetryPolicy.builder
          .backoffStrategy(BackoffStrategy.defaultStrategy)
          .throttlingBackoffStrategy(BackoffStrategy.defaultThrottlingStrategy)
          .numRetries(SdkDefaultRetrySetting.DEFAULT_MAX_RETRIES)
          .retryCondition(RetryCondition.defaultRetryCondition)
          .build
      )
      .build()
  }

  private[this] val credentialsProvider: AwsCredentialsProvider =
    StaticCredentialsProvider.create(AwsBasicCredentials.create(AWS_ACCESS_KEY_ID, AWS_SECRET_KEY_ID))

  implicit val sqsClient: SqsAsyncClient =
    SqsAsyncClient
      .builder()
      .endpointOverride(URI create SECURITY_TOKEN_URL)
      .credentialsProvider(credentialsProvider)
      .region(Region.EU_WEST_1)
      .overrideConfiguration(clientOverrideConfiguration)
      .build()

  /**
   * Source is connected to SQS and polls for messages to stream them events down to the flow.
   * Alpakka library provides SqsSource which is a wrapper over the SQS client from the AWS SDK.
   *
   * @param sqsAsyncClient Asynchronous SQS client
   * @return Akka stream's source
   */
  private def createSource(implicit sqsAsyncClient: SqsAsyncClient): Source[Message, NotUsed] = {
    val sourceSettings = SqsSourceSettings()
      .withWaitTime(20 seconds) // The duration for which the call waits for a message to arrive in the queue before returning.
      //.withMaxBufferSize(1) // Number of element to keep in internal buffer of the source
      //.withMaxBatchSize(1) // Maximum number of messages to return in a batch

    // In case an error occurs in application processing code, the element is dropped and the stream continues
    def sqsSource(supervisionStrategy: Supervision.Decider= Supervision.restartingDecider) = {
      SqsSource(SQS_QUEUE_URL, sourceSettings)(sqsAsyncClient)
      .withAttributes(ActorAttributes.supervisionStrategy(supervisionStrategy))
    }
    // The source must be restarted everytime an error occurs.
    RestartSource.onFailuresWithBackoff(minBackoff = 300.millis, maxBackoff = 5.seconds, randomFactor = 0.25) { () => sqsSource() }
  }

  /**
   * Creates a stage that performs operations on incoming message
   *
   * @return
   */
  private def createFlow: Flow[Message, MessageAction, NotUsed] = {
    /*
     * Parse the body of the message as JSON and then convert it to the case class Person
     * The case class person is logged
     */
    def processMessage(message: Message): Future[Person] = {
      logger info s"Processing message with \n identifier: ${message.messageId()} \n and body ${message.body()} ..."
      import io.circe.parser.parse
      val parsedPerson: Either[circe.Error, Person] = for {
        json <- parse(message.body())
        person <- json.as[Person]
        _ <- {
          logger info s"Successfully parsed message body into '$person'! "
          Right(person)
        }
      } yield person
      Future.fromTry(parsedPerson.toTry)
    }
    import cats.implicits._

    import scala.concurrent.ExecutionContext.Implicits.global

    Flow[Message].mapAsync(1) {
      message =>
        for {
          messageAction <- processMessage(message) *> Future.successful {
            logger info s"Deleting message with \n identifier ${message.messageId()} \n and body: ${message.body()} ..."
            MessageAction.delete(message)
          }
        } yield messageAction
    }
  }
  //FIXME: Deletion doesn't seem to work on localstack!
  createSource
    .via(createFlow)
    .viaMat(KillSwitches.single)(Keep.left)
    .via(SqsAckFlow(SQS_QUEUE_URL))
    .runWith(Sink.head)
  /*
    createSource
    .via(createFlow)
    .viaMat(KillSwitches.single)(Keep.left)
    .toMat(SqsAckSink(SQS_QUEUE_URL))(Keep.both)
    .run()
   */
}
