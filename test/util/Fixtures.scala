/*
 * Copyright 2019 HM Revenue & Customs
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

package util

import java.util.UUID

import akka.stream.Materializer
import javax.inject.{Inject, Singleton}
import models._
import models.addresslookup.{AddressRecord, Country, RecordSet, Address => PafAddress}
import models.dto.AddressDto
import org.joda.time.{DateTime, LocalDate}
import org.mockito.Matchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Suite}
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, RequestHeader, Result}
import play.api.test.FakeRequest
import play.twirl.api.Html
import uk.gov.hmrc.domain.{Generator, Nino, SaUtr}
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}
import uk.gov.hmrc.play.frontend.auth.connectors.domain._
import uk.gov.hmrc.play.frontend.auth.{AuthContext, AuthenticationProviderIds}
import uk.gov.hmrc.play.frontend.filters.CookieCryptoFilter
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.renderer.TemplateRenderer
import uk.gov.hmrc.time.DateTimeUtils._

import scala.concurrent.Future
import scala.io.Source
import scala.reflect.ClassTag
import scala.util.Random

trait PafFixtures {
  val exampleCountryUK = Country("UK","United Kingdom")

  def fakeStreetPafAddressRecord = AddressRecord("GB101", PafAddress(Seq("1 Fake Street","Fake Town","Fake City"),Some("Fake Region"), None, "AA1 1AA",exampleCountryUK), "en")

  val oneOtherPlacePafAddress = PafAddress(Seq("2 Other Place","Some District"),Some("Anytown"), None, "AA1 1AA",exampleCountryUK)
  val twoOtherPlacePafAddress = PafAddress(Seq("3 Other Place","Some District"),Some("Anytown"), None, "AA1 1AA",exampleCountryUK)
  val otherPlacePafDifferentPostcodeAddress = PafAddress(Seq("3 Other Place","Some District"),Some("Anytown"), None, "AA1 2AA",exampleCountryUK)

  val oneOtherPlacePafAddressRecord = AddressRecord("GB990091234514",oneOtherPlacePafAddress,"en")
  val twoOtherPlacePafAddressRecord = AddressRecord("GB990091234515",twoOtherPlacePafAddress,"en")
  val otherPlacePafDifferentPostcodeAddressRecord = AddressRecord("GB990091234516",otherPlacePafDifferentPostcodeAddress,"en")

  def oneAndTwoOtherPlacePafRecordSet = RecordSet(List(
    oneOtherPlacePafAddressRecord,
    twoOtherPlacePafAddressRecord
  ))

  def newPostcodePlacePafRecordSet = RecordSet(List(
    otherPlacePafDifferentPostcodeAddressRecord
  ))


  def oneAndTwoOtherPlacePafRecordSetJson = Source.fromInputStream(getClass.getResourceAsStream("/address-lookup/recordSet.json")).mkString
}

trait TaiFixtures {

  def buildTaxComponents: TaxComponents = TaxComponents(Seq("EmployerProvidedServices", "PersonalPensionPayments"))
}

trait TaxCalculationFixtures {
  def buildTaxCalculation = TaxCalculation("Overpaid", BigDecimal(84.23), 2015, Some("REFUND"), None, None, None)
}

trait CitizenDetailsFixtures {
  def buildPersonDetails = PersonDetails("115", Person(
    Some("Firstname"), Some("Middlename"), Some("Lastname"), Some("FML"),
    Some("Dr"), Some("Phd."), Some("M"), Some(LocalDate.parse("1945-03-18")), Some(Fixtures.fakeNino)
  ), Some( buildFakeAddress ), None)

  def buildFakeAddress = Address(
    Some("1 Fake Street"),
    Some("Fake Town"),
    Some("Fake City"),
    Some("Fake Region"),
    None,
    Some("AA1 1AA"),
    Some(new LocalDate(2015, 3, 15)),
    Some("Residential")
  )

  def buildFakeJsonAddress = Json.toJson(buildFakeAddress)

  def asAddressDto(l: List[(String, String)]) = AddressDto.form.bind(l.toMap).get

  def fakeStreetTupleListAddressForUnmodified: List[(String, String)] = List(
    ("line1", "1 Fake Street"),
    ("line2", "Fake Town"),
    ("line3", "Fake City"),
    ("line4", "Fake Region"),
    ("line5", ""),
    ("postcode", "AA1 1AA")
  )

  def fakeStreetTupleListAddressForManualyEntered: List[(String, String)] = List(
    ("line1", "1 Fake Street"),
    ("line2", "Fake Town"),
    ("line3", "Fake City"),
    ("line4", "Fake Region"),
    ("line5", ""),
    ("postcode", "AA1 1AA")
  )

  def fakeStreetTupleListAddressForModified: List[(String, String)] = List(
    ("line1", "11 Fake Street"), //Note 11 not 1
    ("line2", "Fake Town"),
    ("line3", "Fake City"),
    ("line4", "Fake Region"),
    ("line5", ""),
    ("postcode", "AA1 1AA")
  )

  def fakeStreetTupleListAddressForModifiedPostcode: List[(String, String)] = List(
    ("line1", "11 Fake Street"), //Note 11 not 1
    ("line2", "Fake Town"),
    ("line3", "Fake City"),
    ("line4", "Fake Region"),
    ("line5", ""),
    ("postcode", "AA1 2AA")
  )
}

object Fixtures extends PafFixtures with TaiFixtures with CitizenDetailsFixtures with TaxCalculationFixtures {

  val fakeNino = Nino(new Generator(new Random()).nextNino.nino)

  def buildFakeRequestWithSessionId(method: String) = FakeRequest(method, "/personal-account").withSession("sessionId" -> "FAKE_SESSION_ID")

  def buildFakeRequestWithAuth(method: String, uri: String = "/personal-account"): FakeRequest[AnyContentAsEmpty.type] = {
    val session = Map(
      SessionKeys.sessionId -> s"session-${UUID.randomUUID()}",
      SessionKeys.lastRequestTimestamp -> now.getMillis.toString,
      SessionKeys.userId -> "/auth/oid/flastname",
      SessionKeys.token -> "FAKEGGTOKEN",                                        //NOTE - this is only used by AnyAuthenticationProvider and not this application to determine AP
      SessionKeys.authProvider -> AuthenticationProviderIds.GovernmentGatewayId  //NOTE - this is only used by AnyAuthenticationProvider and not this application to determine AP
    )

    FakeRequest(method, uri).withSession(session.toList: _*)
  }

  def buildFakeRequestWithVerify(method: String, uri: String = "/personal-account"): FakeRequest[AnyContentAsEmpty.type] = {
    val session = Map(
      SessionKeys.sessionId -> s"session-${UUID.randomUUID()}",
      SessionKeys.lastRequestTimestamp -> now.getMillis.toString,
      SessionKeys.userId -> "/auth/oid/flastname",
      SessionKeys.token -> "FAKEVERIFYTOKEN",                                        //NOTE - this is only used by AnyAuthenticationProvider and not this application to determine AP
      SessionKeys.authProvider -> AuthenticationProviderIds.VerifyProviderId  //NOTE - this is only used by AnyAuthenticationProvider and not this application to determine AP
    )

    FakeRequest(method, uri).withSession(session.toList: _*)
  }

  def buildUnusedAllowance = UnusedAllowance(BigDecimal(4000.00))

  def buildFakeAuthContext(withPaye: Boolean = true, withSa: Boolean = false) = AuthContext(buildFakeAuthority(withPaye, withSa), None)

  def buildFakePertaxUser(withPaye: Boolean = true, withSa: Boolean = false, isGovernmentGateway: Boolean = false, isHighGG: Boolean = false) =
    new PertaxUser(authContext = buildFakeAuthContext(withPaye, withSa),
      if(isGovernmentGateway) UserDetails(UserDetails.GovernmentGatewayAuthProvider) else UserDetails(UserDetails.VerifyAuthProvider),
      personDetails = None,
      isHighGG)

  def buildFakeAuthority(withPaye: Boolean = true, withSa: Boolean = false,
    confidenceLevel: ConfidenceLevel = ConfidenceLevel.L0, nino: Nino = Fixtures.fakeNino, userDetailsLink: Option[String] = Some("/userDetailsLink")) = Authority(
    uri = "/auth/oid/flastname",
    accounts = Accounts(
      paye = if(withPaye) Some(PayeAccount("/paye/"+nino.nino, nino)) else None,
      sa = if(withSa) Some(SaAccount("/sa/1111111111", SaUtr("1111111111"))) else None
    ),
    loggedInAt = None,
    previouslyLoggedInAt = Some(DateTime.parse("1982-04-30T00:00:00.000+01:00")),
    credentialStrength = CredentialStrength.Strong,
    confidenceLevel = confidenceLevel,
    userDetailsLink = userDetailsLink,
    enrolments = Some("/userEnrolmentsLink"),
    ids = None,
    legacyOid = ""
  )

  def buildFakeHeaderCarrier = MockitoSugar.mock[HeaderCarrier]

  def buildFakePersonDetails = PersonDetails("115", buildFakePerson, None, None)

  def buildFakePerson = Person(Some("Firstname"), Some("Middlename"), Some("Lastname"), Some("FML"), Some("Mr"),
    None, Some("M"), Some(LocalDate.parse("1931-01-17")), Some(Fixtures.fakeNino))

}

@Singleton
class FakeCookieCryptoFilter @Inject()(override val mat: Materializer) extends CookieCryptoFilter {


  override protected val encrypter: (String) => String = x => x
  override protected val decrypter: (String) => String = x => x

  override def apply(next: (RequestHeader) => Future[Result])(rh: RequestHeader) =
    next(rh)
}

trait BaseSpec extends UnitSpec with OneAppPerSuite with PatienceConfiguration with BeforeAndAfterEach { this: Suite =>

  implicit val hc = HeaderCarrier()

  lazy val localGuiceApplicationBuilder = GuiceApplicationBuilder()
    .overrides(bind[TemplateRenderer].toInstance(MockTemplateRenderer))
    .overrides(bind[CookieCryptoFilter].to(classOf[FakeCookieCryptoFilter]))

  override implicit lazy val app: Application = localGuiceApplicationBuilder.build()

  def injected[T](c: Class[T]): T = app.injector.instanceOf(c)
  def injected[T](implicit evidence: ClassTag[T]) = app.injector.instanceOf[T]

  val mockLocalPartialRetreiver: LocalPartialRetriever = {
    val pr = MockitoSugar.mock[LocalPartialRetriever]
    when(pr.getPartialContent(any(),any(),any())(any())) thenReturn Html("")
    pr
  }

}
