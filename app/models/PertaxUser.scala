/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package models

import java.security.cert.X509Certificate

import config.ConfigDecorator
import play.api.mvc.{AnyContent, Headers, Request}
import play.twirl.api.Html
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.auth.connectors.domain.CredentialStrength
import uk.gov.hmrc.play.http.SessionKeys
import util.LocalPartialRetriever

class AuthContextDecorator(authContext: Option[AuthContext]) { //FIXME - PertaxContext should probably be refactored to 1. Contain an AuthContext 2. Implement this
  def enrolmentUri: Option[String] = authContext.flatMap(_.enrolmentsUri)
  def userDetailsUri: Option[String] = authContext.flatMap(_.userDetailsUri)
  def isSa: Boolean = authContext.fold(false)(_.principal.accounts.sa.isDefined)
  def isPaye: Boolean = authContext.fold(false)(_.principal.accounts.paye.isDefined)
  def nino: Option[Nino] = authContext.flatMap(_.principal.accounts.paye.map(_.nino))
  def saUtr: Option[SaUtr] = authContext.flatMap(_.principal.accounts.sa.map(_.utr))
}

class PertaxContext(val request: Request[AnyContent], val partialRetriever: LocalPartialRetriever, val configDecorator: ConfigDecorator, val user: Option[PertaxUser],
                    val breadcrumb: Option[Breadcrumb], val welshWarning: Boolean) extends Request[AnyContent] {
  override def body: AnyContent = request.body
  override def secure: Boolean = request.secure
  override def uri: String = request.uri
  override def queryString: Map[String, Seq[String]] = request.queryString
  override def remoteAddress: String = request.remoteAddress
  override def method: String = request.method
  override def headers: Headers = request.headers
  override def path: String = request.path
  override def version: String = request.version
  override def tags: Map[String, String] = request.tags
  override def id: Long = request.id
  override def clientCertificateChain: Option[Seq[X509Certificate]] = request.clientCertificateChain
  def withUser(u: Option[PertaxUser]) = new PertaxContext(request, partialRetriever, configDecorator, u, breadcrumb, welshWarning)
  def withBreadcrumb(b: Option[Breadcrumb]) = new PertaxContext(request, partialRetriever, configDecorator, user, b, welshWarning)

  def authContext = user.map(_.authContext)

  def authProvider = request.session.get(SessionKeys.authProvider)

  def userAndNino: Option[(PertaxUser, Nino)] = for(u <- user; n <- u.nino) yield (u,n)
  def nino: Option[Nino] = userAndNino.map(_._2)

  def enrolmentUri = for(u <- user; uri <- u.authContext.enrolmentsUri) yield uri

  def getPartialContent(url: String): Html = partialRetriever.getPartialContent(url)(this)

  def withWelshWarning(ww: Boolean) =
    new PertaxContext(request, partialRetriever, configDecorator, user, breadcrumb, ww)
}

object PertaxContext {
  def apply(request: Request[AnyContent], partialRetriever: LocalPartialRetriever, configDecorator: ConfigDecorator, user: Option[PertaxUser] = None,
            breadcrumb: Option[Breadcrumb] = None, welshWarning: Boolean = false): PertaxContext = new PertaxContext(request, partialRetriever, configDecorator, user, breadcrumb, welshWarning)
}

case class PertaxUser(val authContext: AuthContext, val userDetails: UserDetails, val personDetails: Option[PersonDetails], private val isHighGG: Boolean) {
  
  import models.PertaxUser._

  val nino: Option[Nino] = authContext.principal.accounts.paye.map(_.nino)
  val saUtr: Option[SaUtr] = authContext.principal.accounts.sa.map(_.utr)

  def name: Option[String] = personDetails match {
    case Some(personDetails) => personDetails.person.shortName
    case _ => authContext.principal.name
  }

  def nameOrAttorneyName: Option[String] = {
    authContext.attorney.map(a => a.name).orElse(name)
  }

  private def accounts = authContext.principal.accounts

  def isSa = accounts.sa.isDefined
  def isPaye = accounts.paye.isDefined

  def isVerify = userDetails.hasVerifyAuthProvider
  def isGovernmentGateway = userDetails.hasGovernmentGatewayAuthProvider

  def isLowGovernmentGateway = isGovernmentGateway && !isHighGG
  def isHighGovernmentGateway = isGovernmentGateway && isHighGG
  def isHighGovernmentGatewayOrVerify = isHighGovernmentGateway || isVerify

  def hasPersonDetails = personDetails.isDefined

  def hasHighCredStrength = authContext.user.credentialStrength == CredentialStrength.Strong

  def authCondition: AuthCondition = {
    if(isLowGovernmentGateway)       LowGovernmentGateway(accounts.sa.map(_.utr), accounts.paye.map(_.nino))
    else if(isHighGovernmentGateway) HighGovernmentGateway(accounts.sa.map(_.utr), accounts.paye.map(_.nino))
    else if(isVerify)                Verify(accounts.sa.map(_.utr), accounts.paye.map(_.nino))
    else throw new RuntimeException("Undefined auth condition")
  }

  def confidenceLevel = authContext.user.confidenceLevel

  def withPersonDetails(personDetails: Option[PersonDetails]) = new PertaxUser(authContext, userDetails, personDetails, isHighGG)
}

object PertaxUser {
  
  sealed trait AuthCondition {
    def saUtr: Option[SaUtr]
    def nino: Option[Nino]
  }
  case class LowGovernmentGateway(saUtr: Option[SaUtr], nino: Option[Nino]) extends AuthCondition
  case class HighGovernmentGateway(saUtr: Option[SaUtr], nino: Option[Nino]) extends AuthCondition
  case class Verify(saUtr: Option[SaUtr], nino: Option[Nino]) extends AuthCondition
  
  //Loan helpers
  
  def ifUserPredicate[T](condition: PertaxUser => Boolean)(block: PertaxUser => T)(implicit pertaxContext: PertaxContext) =
    pertaxContext.user.filter(u => condition(u)).map(u => block(u))
  def ifAuthenticatedUser[T](block: => T)(implicit pertaxContext: PertaxContext)                 = pertaxContext.user.map(u => block)
  def ifGovernmentGatewayUser[T](block: => T)(implicit pertaxContext: PertaxContext)             = ifUserPredicate(_.isGovernmentGateway)(u => block)
  def ifLowGovernmentGatewayUser[T](block: => T)(implicit pertaxContext: PertaxContext)          = ifUserPredicate(_.isLowGovernmentGateway)(u => block)
  def ifHighGovernmentGatewayUser[T](block: => T)(implicit pertaxContext: PertaxContext)         = ifUserPredicate(_.isHighGovernmentGateway)(u => block)
  def ifVerifyUser[T](block: => T)(implicit pertaxContext: PertaxContext)                        = ifUserPredicate(_.isVerify)(u => block)
  def ifHighGovernmentGatewayOrVerifyUser[T](block: => T)(implicit pertaxContext: PertaxContext) = ifUserPredicate(_.isHighGovernmentGatewayOrVerify)(u => block)
  def ifSaUser[T](block: => T)(implicit pertaxContext: PertaxContext)                            = ifUserPredicate(_.isSa)(u => block)
  def ifPayeOrSaUser[T](block: => T)(implicit pertaxContext: PertaxContext)                      = ifUserPredicate(u => u.isSa || u.isPaye)(u => block)
  def ifPayeUser[T](block: => T)(implicit pertaxContext: PertaxContext)                          = ifUserPredicate(_.isPaye)(u => block)
  def ifPayeUserLoanNino[T](block: Nino => T)(implicit pertaxContext: PertaxContext)             = ifUserPredicate(_.isPaye)(u => u.nino.map(block))
  def ifNonDelegatingUser[T](block: => T)(implicit pertaxContext: PertaxContext)                 = ifUserPredicate(!_.authContext.isDelegating)(u => block)
  def ifUserHasPersonDetails[T](block: => T)(implicit pertaxContext: PertaxContext)              = ifUserPredicate(_.hasPersonDetails)(u => block)
  def unlessUserHasPersonDetails[T](block: => T)(implicit pertaxContext: PertaxContext)          = ifUserPredicate(!_.hasPersonDetails)(u => block)
  def ifNameAvailable[T](block: => T)(implicit pertaxContext: PertaxContext)                     = ifUserPredicate(_.name.isDefined)(u => block)
  def unlessNameAvailable[T](block: => T)(implicit pertaxContext: PertaxContext)                 = ifUserPredicate(_.name.isEmpty)(u => block)
}
