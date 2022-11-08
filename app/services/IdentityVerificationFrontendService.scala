package services

import cats.data.EitherT
import com.google.inject.Inject
import connectors.IdentityVerificationFrontendConnector
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

sealed trait IdentityVerificationResponse

case object Success extends IdentityVerificationResponse {
  override def toString: String = "Success"}
case object Incomplete extends IdentityVerificationResponse {
  override def toString: String = "Incomplete"}
case object FailedMatching extends IdentityVerificationResponse {
  override def toString: String = "FailedMatching"}
case object InsufficientEvidence extends IdentityVerificationResponse {
  override def toString: String = "InsufficientEvidence"}
case object LockedOut extends IdentityVerificationResponse {
  override def toString: String = "LockedOut"}
case object UserAborted extends IdentityVerificationResponse {
  override def toString: String = "UserAborted"}
case object Timeout extends IdentityVerificationResponse {
  override def toString: String = "Timeout"}
case object TechnicalIssue extends IdentityVerificationResponse {
  override def toString: String = "TechnicalIssue"}
case object PrecondFailed extends IdentityVerificationResponse {
  override def toString: String = "PreconditionFailed"}

class IdentityVerificationFrontendService @Inject()(identityVerificationFrontendConnector: IdentityVerificationFrontendConnector) {

  def getIVJourneyStatus(journeyId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, UpstreamErrorResponse, IdentityVerificationResponse] = {
    identityVerificationFrontendConnector.getIVJourneyStatus(journeyId).map { response =>
      val status = List(
                      (response.json \ "journeyResult").asOpt[String],
                      (response.json \ "result").asOpt[String]
                    ).flatten.head //FIXME - dont use head
      status match {
        case "Success" => Success
        case "Incomplete" => Incomplete
        case "FailedMatching" => FailedMatching
        case "InsufficientEvidence" => InsufficientEvidence
        case "LockedOut" => LockedOut
        case "UserAborted" => UserAborted
        case "Timeout" => Timeout
        case "TechnicalIssue" => TechnicalIssue
        case "PreconditionFailed" => PrecondFailed
      }
    }
  }

}
