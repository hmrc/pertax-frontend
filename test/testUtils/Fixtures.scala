/*
 * Copyright 2024 HM Revenue & Customs
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

package testUtils

import controllers.auth.requests.UserRequest
import models._
import models.addresslookup.{Address => PafAddress, AddressRecord, Country, RecordSet}
import models.dto.AddressDto
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.domain.{Generator, Nino, SaUtr, SaUtrGenerator}
import uk.gov.hmrc.http.SessionKeys

import java.time.temporal.ChronoField
import java.time.{Instant, LocalDate}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

trait PafFixtures {

  val exampleCountryUK: Country        = Country("UK", "United Kingdom")
  val exampleCountryOutsideUK: Country = Country("FR", "France")
  val subDivision: Option[Country]     = Some(Country("GB-ENG", "England"))

  val fakeStreetPafAddressRecordOutsideUk: AddressRecord = AddressRecord(
    "GB101",
    PafAddress(
      List("1 Fake Street", "Fake Town", "Fake City"),
      Some("Fake Region"),
      None,
      "AA1 1AA",
      subDivision,
      exampleCountryOutsideUK
    ),
    "en"
  )

  val fakeStreetPafAddressRecord: AddressRecord = AddressRecord(
    "GB101",
    PafAddress(
      List("1 Fake Street", "Fake Town"),
      Some("Fake City"),
      None,
      "AA1 1AA",
      subDivision,
      exampleCountryUK
    ),
    "en"
  )

  val oneOtherPlacePafAddress: PafAddress =
    PafAddress(
      List("2 Other Place", "Some District"),
      Some("Anytown"),
      None,
      "AA1 1AA",
      subDivision,
      exampleCountryUK
    )

  val twoOtherPlacePafAddress: PafAddress =
    PafAddress(
      List("3 Other Place", "Some District"),
      Some("Anytown"),
      None,
      "AA1 1AA",
      Some(Country("GB-SCT", "Scotland")),
      exampleCountryUK
    )

  val otherPlacePafDifferentPostcodeAddress: PafAddress =
    PafAddress(
      List("3 Other Place", "Some District"),
      Some("Anytown"),
      None,
      "AA1 2AA",
      subDivision,
      exampleCountryUK
    )

  val oneOtherPlacePafAddressRecord: AddressRecord               = AddressRecord("GB990091234514", oneOtherPlacePafAddress, "en")
  val twoOtherPlacePafAddressRecord: AddressRecord               = AddressRecord("GB990091234515", twoOtherPlacePafAddress, "en")
  val otherPlacePafDifferentPostcodeAddressRecord: AddressRecord =
    AddressRecord("GB990091234516", otherPlacePafDifferentPostcodeAddress, "en")

  val oneAndTwoOtherPlacePafRecordSet: RecordSet = RecordSet(
    List(
      oneOtherPlacePafAddressRecord,
      twoOtherPlacePafAddressRecord
    )
  )

  val newPostcodePlacePafRecordSet: RecordSet = RecordSet(
    List(
      otherPlacePafDifferentPostcodeAddressRecord
    )
  )

  val twoOtherPlaceRecordSet: RecordSet = RecordSet(
    List(twoOtherPlacePafAddressRecord)
  )
}

trait TaiFixtures {

  def buildTaxComponents: List[String] = List("EmployerProvidedServices", "PersonalPensionPayments")
}

trait CitizenDetailsFixtures {
  def buildPersonDetailsWithPersonalAndCorrespondenceAddress: PersonDetails =
    PersonDetails(
      "115",
      Person(
        Some("Firstname"),
        Some("Middlename"),
        Some("Lastname"),
        Some("FML"),
        Some("Dr"),
        Some("Phd."),
        Some("M"),
        Some(LocalDate.parse("1945-03-18")),
        Some(Fixtures.fakeNino)
      ),
      Some(buildFakeAddress),
      Some(buildFakeCorrespondenceAddress)
    )

  def buildPersonDetails: PersonDetails =
    PersonDetails(
      "115",
      Person(
        Some("Firstname"),
        Some("Middlename"),
        Some("Lastname"),
        Some("FML"),
        Some("Dr"),
        Some("Phd."),
        Some("M"),
        Some(LocalDate.parse("1945-03-18")),
        Some(Fixtures.fakeNino)
      ),
      Some(buildFakeAddress),
      None
    )

  def buildPersonDetailsCorrespondenceAddress: PersonDetails =
    PersonDetails(
      "115",
      Person(
        Some("Firstname"),
        Some("Middlename"),
        Some("Lastname"),
        Some("FML"),
        Some("Dr"),
        Some("Phd."),
        Some("M"),
        Some(LocalDate.parse("1945-03-18")),
        Some(Fixtures.fakeNino)
      ),
      Some(buildFakeAddress),
      Some(buildFakeCorrespondenceAddress.copy(postcode = Some("CC1 1AA")))
    )

  def buildFakeAddress: Address = Address(
    Some("1 Fake Street"),
    Some("Fake Town"),
    Some("Fake City"),
    Some("Fake Region"),
    None,
    Some("AA1 1AA"),
    None,
    Some(LocalDate.of(2015, 3, 15)),
    None,
    Some("Residential"),
    isRls = false
  )

  def buildFakeCorrespondenceAddress: Address = Address(
    Some("1 Fake Street"),
    Some("Fake Town"),
    Some("Fake City"),
    Some("Fake Region"),
    None,
    Some("AA1 1AA"),
    None,
    Some(LocalDate.of(2015, 3, 15)),
    None,
    Some("Correspondence"),
    isRls = false
  )

  def buildFakeAddressWithEndDate: Address = Address(
    Some("1 Fake Street"),
    Some("Fake Town"),
    Some("Fake City"),
    Some("Fake Region"),
    None,
    Some("AA1 1AA"),
    None,
    Some(LocalDate.now),
    Some(LocalDate.now),
    Some("Correspondence"),
    isRls = false
  )

  def buildFakeJsonAddress: JsValue = Json.toJson(buildFakeAddress)

  def asAddressDto(l: List[(String, String)]): AddressDto = AddressDto.ukForm.bind(l.toMap).get

  def asInternationalAddressDto(l: List[(String, String)]): AddressDto = AddressDto.internationalForm.bind(l.toMap).get

  def fakeStreetTupleListAddressForUnmodified: List[(String, String)] = List(
    ("line1", "1 Fake Street"),
    ("line2", "Fake Town"),
    ("line4OrTown", "Fake City"),
    ("line5OrCounty", "Fake Region"),
    ("postcode", "AA1 1AA")
  )

  def fakeStreetTupleListAddressForUnmodifiedNoRegion: List[(String, String)] = List(
    ("line1", "1 Fake Street"),
    ("line2", "Fake Town"),
    ("line4OrTown", "Fake City"),
    ("postcode", "AA1 1AA")
  )

  def fakeStreetTupleListInternationalAddress: List[(String, String)] = List(
    ("line1", "1 Fake Street"),
    ("line2", "Fake Town"),
    ("line4OrTown", "Fake City"),
    ("line5OrCounty", ""),
    ("country", "South Georgia and South Sandwich Island")
  )

  def fakeStreetTupleListAddressForUnmodifiedLowerCase: List[(String, String)] = List(
    ("line1", "1 Fake Street"),
    ("line2", "Fake Town"),
    ("line4OrTown", "Fake City"),
    ("line5OrCounty", ""),
    ("postcode", "aa1 1aa")
  )

  def fakeStreetTupleListAddressForUnmodifiedNoSpaceInPostcode: List[(String, String)] = List(
    ("line1", "1 Fake Street"),
    ("line2", "Fake Town"),
    ("line4OrTown", "Fake City"),
    ("line5OrCounty", ""),
    ("postcode", "AA11AA")
  )

  def fakeStreetTupleListAddressForManuallyEntered: List[(String, String)] = List(
    ("line1", "1 Fake Street"),
    ("line2", "Fake Town"),
    ("line4OrTown", "Fake City"),
    ("line5OrCounty", ""),
    ("postcode", "AA1 1AA")
  )

  def fakeStreetTupleListAddressForModified: List[(String, String)] = List(
    ("line1", "11 Fake Street"), // Note 11 not 1
    ("line2", "Fake Town"),
    ("line4OrTown", "Fake City"),
    ("line5OrCounty", ""),
    ("postcode", "AA1 1AA")
  )

  def fakeStreetTupleListAddressForModifiedPostcode: List[(String, String)] = List(
    ("line1", "11 Fake Street"), // Note 11 not 1
    ("line2", "Fake Town"),
    ("line4OrTown", "Fake City"),
    ("line5OrCounty", ""),
    ("postcode", "AA1 2AA")
  )
}

object Fixtures extends PafFixtures with TaiFixtures with CitizenDetailsFixtures {

  val fakeNino: Nino = Nino(new Generator(new Random()).nextNino.nino)

  val saUtr: SaUtr = new SaUtrGenerator().nextSaUtr

  val etag = "1"

  def buildFakeRequestWithSessionId(method: String): FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(method, "/personal-account").withSession("sessionId" -> "FAKE_SESSION_ID")

  def buildFakeRequestWithAuth(
    method: String,
    uri: String = "/personal-account"
  ): FakeRequest[AnyContentAsEmpty.type] = {
    val session = Map(
      SessionKeys.sessionId            -> s"session-${UUID.randomUUID()}",
      SessionKeys.lastRequestTimestamp -> Instant.now().get(ChronoField.MILLI_OF_SECOND).toString
    )

    FakeRequest(method, uri).withSession(session.toList: _*)
  }

  def buildUnusedAllowance: UnusedAllowance = UnusedAllowance(BigDecimal(4000.00))

  def buildFakePersonDetails: PersonDetails = PersonDetails("115", buildFakePerson, None, None)

  def buildFakePerson: Person =
    Person(
      Some("Firstname"),
      Some("Middlename"),
      Some("Lastname"),
      Some("FML"),
      Some("Mr"),
      None,
      Some("M"),
      Some(LocalDate.parse("1931-01-17")),
      Some(Fixtures.fakeNino)
    )

}

trait ActionBuilderFixture extends ActionBuilder[UserRequest, AnyContent] {
  override def invokeBlock[A](a: Request[A], block: UserRequest[A] => Future[Result]): Future[Result]
  override def parser: BodyParser[AnyContent]               = Helpers.stubBodyParser()
  override protected def executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
}
