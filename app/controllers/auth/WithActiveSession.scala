package controllers.auth

import com.google.inject.{ImplementedBy, Inject}
import config.ConfigDecorator
import controllers.auth.requests.{AuthenticatedRequest, UserRequest}
import play.api.mvc.Results.Redirect
import play.api.mvc.{ActionFilter, AnyContent, Request, Result}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

class WithActiveSessionImpl @Inject()(configDecorator: ConfigDecorator)(implicit ec: ExecutionContext) extends WithActiveSession {

  override protected def filter[A](request: Request[A]): Future[Option[Result]] =
    if (request.session.isEmpty) Future.successful(Some(Redirect(configDecorator.authProviderChoice)))
    else Future.successful(None)

  override protected def executionContext: ExecutionContext = ec

}
@ImplementedBy(classOf[WithActiveSessionImpl])
trait WithActiveSession extends ActionFilter[Request[AnyContent]]