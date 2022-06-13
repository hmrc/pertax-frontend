package connectors

import config.ConfigDecorator
import uk.gov.hmrc.circuitbreaker.{CircuitBreakerConfig, UsingCircuitBreaker}
import uk.gov.hmrc.http.HttpReads.is5xx
import uk.gov.hmrc.http.UpstreamErrorResponse.Upstream5xxResponse
import uk.gov.hmrc.http.{HttpException, TooManyRequestException}

trait ServicesCircuitBreaker extends UsingCircuitBreaker {

  protected val externalServiceName: String

  val configDecorator : ConfigDecorator

  override protected def circuitBreakerConfig = CircuitBreakerConfig(
    serviceName = externalServiceName,
    numberOfCallsToTriggerStateChange = configDecorator.numberOfCallsToTriggerStateChange(externalServiceName),
  unavailablePeriodDuration = configDecorator.unavailablePeriodDuration(externalServiceName),
  unstablePeriodDuration=  configDecorator.unstablePeriodDuration(externalServiceName)
  )


  object Is5xx {
    def unapply(throwable: Throwable): Option[Int] =
      throwable match {
        case exc: HttpException if is5xx(exc.responseCode) => Some(exc.responseCode)
        case Upstream5xxResponse(error) => Some(error.statusCode)
        case _ => None
      }
  }

  override def breakOnException(throwable: Throwable): Boolean =
    throwable match {
      case throwable: TooManyRequestException => true
      case Is5xx(_) => true
      case _ => false
    }


}
