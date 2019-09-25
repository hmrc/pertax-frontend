package controllers.auth

import com.google.inject.{ImplementedBy, Inject}
import connectors.NewPertaxAuthConnector
import controllers.auth.requests.{AuthenticatedRequest, RefinedRequest}
import models.{ActivatedOnlineFilerSelfAssessmentUser, AmbiguousFilerSelfAssessmentUser, NonFilerSelfAssessmentUser, NotYetActivatedOnlineFilerSelfAssessmentUser, SelfAssessmentUserType}
import play.api.Configuration
import play.api.mvc.{ActionBuilder, ActionFunction, ActionRefiner, Request, Result}
import services.{CitizenDetailsService, MatchingDetailsSuccessResponse, SelfAssessmentService}
import uk.gov.hmrc.auth.core.AuthorisedFunctions

import scala.concurrent.{ExecutionContext, Future}

class SelfAssessmentStatusAction @Inject()(citizenDetailsService: CitizenDetailsService,
                                           selfAssessmentService: SelfAssessmentService)(
  implicit ec: ExecutionContext)
  extends ActionRefiner[AuthenticatedRequest, RefinedRequest] with ActionFunction[Request, AuthenticatedRequest] {

  override protected def refine[A](request: AuthenticatedRequest[A]): Future[Either[Result, RefinedRequest[A]]] = {
    citizenDetailsService.getMatchingDetails(nino) map {
      case MatchingDetailsSuccessResponse(matchingDetails) => matchingDetails.saUtr
      case _                                               => None
    }

    context.nino.fold[Future[SelfAssessmentUserType]](Future.successful(NonFilerSelfAssessmentUser)) { nino =>
      context.saUtr.fold({
        selfAssessmentService.getSaUtrIfUserHasNotYetActivatedSaEnrolment flatMap {
          case Some(saUtr) =>
            Future.successful(NotYetActivatedOnlineFilerSelfAssessmentUser(saUtr)) //NotYetActivated online filer
          case None => {
            selfAssessmentService.getSaUtrFromCitizenDetailsService(nino) map {
              case Some(saUtr) => AmbiguousFilerSelfAssessmentUser(saUtr) //Ambiguous SA filer
              case None        => NonFilerSelfAssessmentUser //Non SA Filer
            }
          }
        }
      })({ saUtr =>
        Future.successful(ActivatedOnlineFilerSelfAssessmentUser(saUtr)) //Activated online filer
      })

    }
  }
  }
}
