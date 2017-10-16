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

package controllers

import config.ConfigDecorator
import connectors.{FrontEndDelegationConnector, PertaxAuditConnector, PertaxAuthConnector}
import controllers.bindable.{Origin, PostalAddrType, PrimaryAddrType, SoleAddrType}
import models.addresslookup.{AddressRecord, Country, RecordSet, _}
import models.dto._
import models.{AddressJourneyData, PertaxContext, PertaxUser, UserDetails}
import org.joda.time.{DateTime, LocalDate}
import org.jsoup.Jsoup
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.Results
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{AddressLookupResponse, _}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.frontend.auth.connectors.domain.ConfidenceLevel
import util.Fixtures._
import util.{BaseSpec, Fixtures, LocalPartialRetriever}

import scala.concurrent.Future
import uk.gov.hmrc.http.HttpResponse

class AddressControllerSpec extends BaseSpec  {

  override implicit lazy val app: Application = localGuiceApplicationBuilder
    .overrides(bind[CitizenDetailsService].toInstance(MockitoSugar.mock[CitizenDetailsService]))
    .overrides(bind[UserDetailsService].toInstance(MockitoSugar.mock[UserDetailsService]))
    .overrides(bind[AddressLookupService].toInstance(MockitoSugar.mock[AddressLookupService]))
    .overrides(bind[FrontEndDelegationConnector].toInstance(MockitoSugar.mock[FrontEndDelegationConnector]))
    .overrides(bind[LocalSessionCache].toInstance(MockitoSugar.mock[LocalSessionCache]))
    .overrides(bind[PertaxAuditConnector].toInstance(MockitoSugar.mock[PertaxAuditConnector]))
    .overrides(bind[PertaxAuthConnector].toInstance(MockitoSugar.mock[PertaxAuthConnector]))
    .overrides(bind[LocalPartialRetriever].toInstance(MockitoSugar.mock[LocalPartialRetriever]))
    .overrides(bind[AddressControllerConfiguration].toInstance(new AddressControllerConfiguration {
      override val maxStartDate = DateTime.parse("2016-02-02").withTimeAtStartOfDay
    }))
    .overrides(bind[ConfigDecorator].toInstance(MockitoSugar.mock[ConfigDecorator]))
    .build()


  override def beforeEach: Unit = {
    reset(injected[LocalSessionCache], injected[CitizenDetailsService], injected[PertaxAuditConnector])
  }

  def buildAddressRequest(method: String, uri: String = "/test") = buildFakeRequestWithAuth(method, uri)

  trait WithAddressControllerSpecSetup {

    def fakeAddress: models.Address
    def nino: Nino
    def personDetailsResponse: PersonDetailsResponse
    def sessionCacheResponse: Option[CacheMap]
    def thisYearStr: String
    def updateAddressResponse: UpdateAddressResponse

    lazy val personDetails = Fixtures.buildPersonDetails

    def asAddressDto(l: List[(String, String)]): AddressDto = AddressDto.form.bind(l.toMap).get

    def pruneDataEvent(dataEvent: DataEvent): DataEvent =
      dataEvent.copy(tags = dataEvent.tags - "X-Request-Chain" - "X-Session-ID" - "token",detail = dataEvent.detail - "credId")

    lazy val controller = {
      val c = injected[AddressController]

      when(injected[PertaxAuditConnector].sendEvent(any())(any(), any())) thenReturn {
        Future.successful(AuditResult.Success)
      }
      when(injected[PertaxAuthConnector].currentAuthority(any(), any())) thenReturn {
        Future.successful(Some(buildFakeAuthority(confidenceLevel = ConfidenceLevel.L200)))
      }
      when(injected[CitizenDetailsService].personDetails(meq(nino))(any())) thenReturn {
        Future.successful(personDetailsResponse)
      }
      when(injected[CitizenDetailsService].updateAddress(meq(nino), meq("115"), meq(fakeAddress))(any())) thenReturn {
        Future.successful(updateAddressResponse)
      }
      when(injected[UserDetailsService].getUserDetails(any())(any())) thenReturn {
        Future.successful(Some(UserDetails(UserDetails.GovernmentGatewayAuthProvider)))
      }
      when(injected[LocalSessionCache].cache(any(), any())(any(), any(), any())) thenReturn {
        Future.successful(CacheMap("id", Map.empty))
      }
      when(injected[LocalSessionCache].fetch()(any(), any())) thenReturn {
        Future.successful(sessionCacheResponse)
      }
      when(c.configDecorator.tcsChangeAddressUrl) thenReturn "/tax-credits-service/personal/change-address"
      when(c.configDecorator.ssoUrl) thenReturn Some("ssoUrl")
      when(c.configDecorator.taxCreditsEnabled) thenReturn true
      when(c.configDecorator.getFeedbackSurveyUrl(any())) thenReturn "/test"
      c
    }
  }

  "Calling AddressController.personalDetails" should {

    trait LocalSetup extends WithAddressControllerSpecSetup {
      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(personDetails)
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"
    }

    "call citizenDetailsService.personDetails and return 200" in new LocalSetup {
      lazy val sessionCacheResponse = Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      val r = controller.personalDetails(buildAddressRequest("GET", uri = "/personal-account/personal-details"))

      status(r) shouldBe OK
      verify(controller.citizenDetailsService, times(1)).personDetails(meq(nino))(any())
      verify(controller.sessionCache, times(1)).cache(meq("addressPageVisitedDto"), meq(AddressPageVisitedDto(true)))(any(), any(), any())
    }
  }


  "Calling AddressController.taxCreditsChoice" should {

    trait LocalSetup extends WithAddressControllerSpecSetup {
      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(personDetails)
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"
    }

    "return OK if there is an entry in the cache to say the user previously visited the 'personal details' page" in new LocalSetup {
      lazy val sessionCacheResponse = Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      val r = controller.taxCreditsChoice(buildFakeRequestWithAuth("GET"))

      status(r) shouldBe OK
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
    }

    "redirect back to the start of the journey if there is no entry in the cache to say the user previously visited the 'personal details' page" in new WithAddressControllerSpecSetup with LocalSetup {
      lazy val sessionCacheResponse = None

      val r = controller.taxCreditsChoice(buildFakeRequestWithAuth("GET"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/personal-details")
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
    }

  }


  "Calling AddressController.processTaxCreditsChoice" should {

    trait LocalSetup extends WithAddressControllerSpecSetup {
      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(personDetails)
      override lazy val sessionCacheResponse = Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"
    }

    "redirect to expected tax credits page when supplied with value = Yes (true)" in new LocalSetup {
      val r = controller.processTaxCreditsChoice(buildFakeRequestWithAuth("POST").withFormUrlEncodedBody( "value" -> "true" ))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/tax-credits-service/personal/change-address")
    }

    "redirect to ResidencyChoice page when supplied with value = No (false)" in new LocalSetup {
      val r = controller.processTaxCreditsChoice(buildFakeRequestWithAuth("POST").withFormUrlEncodedBody("value" -> "false"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/your-address/residency-choice")
    }

    "return a bad request when supplied no value" in new LocalSetup {
      val r = controller.processTaxCreditsChoice(buildFakeRequestWithAuth("POST"))

      status(r) shouldBe BAD_REQUEST
    }

  }


  "Calling AddressController.residencyChoice" should {

    trait LocalSetup extends WithAddressControllerSpecSetup {
      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(personDetails)
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"
    }

    "return OK when the user has indicated that they do not receive tax credits on the previous page" in new LocalSetup {
      lazy val sessionCacheResponse = Some(CacheMap("id", Map("taxCreditsChoiceDto" -> Json.toJson(TaxCreditsChoiceDto(false)))))

      val r = controller.residencyChoice(buildFakeRequestWithAuth("GET"))

      status(r) shouldBe OK
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
    }

    "return to the beginning of journey when the user has indicated that they receive tax credits on the previous page" in new LocalSetup {
      lazy val sessionCacheResponse = Some(CacheMap("id", Map("taxCreditsChoiceDto" -> Json.toJson(TaxCreditsChoiceDto(true)))))

      val r = controller.residencyChoice(buildFakeRequestWithAuth("GET"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/personal-details")
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
    }

    "return to the beginning of journey when the user has not selected any tax credits choice on the previous page" in new LocalSetup {
      lazy val sessionCacheResponse = None

      val r = controller.residencyChoice(buildFakeRequestWithAuth("GET"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/personal-details")
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
    }

  }


  "Calling AddressController.processResidencyChoice" should {

    trait LocalSetup extends WithAddressControllerSpecSetup {
      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(personDetails)
      override lazy val sessionCacheResponse = Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"
    }

    "redirect to find address page with primary type when supplied value=primary" in new LocalSetup {
      val r = controller.processResidencyChoice(buildFakeRequestWithAuth("POST").withFormUrlEncodedBody( "residencyChoice" -> "primary" ))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/your-address/primary/find-address")
    }

    "redirect to find address page with sole type when supplied value=sole" in new LocalSetup {
      val r = controller.processResidencyChoice(buildFakeRequestWithAuth("POST").withFormUrlEncodedBody( "residencyChoice" -> "sole" ))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/your-address/sole/find-address")
    }

    "return a bad request when supplied value=bad" in new LocalSetup {
      val r = controller.processResidencyChoice(buildFakeRequestWithAuth("POST").withFormUrlEncodedBody( "residencyChoice" -> "bad" ))

      status(r) shouldBe BAD_REQUEST
    }

    "return a bad request when supplied no value" in new LocalSetup {
      val r = controller.processResidencyChoice(buildFakeRequestWithAuth("POST"))

      status(r) shouldBe BAD_REQUEST
    }

  }


  "Calling AddressController.showPostcodeLookupForm" should {

    trait LocalSetup extends WithAddressControllerSpecSetup {
      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(personDetails)
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"
    }

    "return 200 if the user has entered a residency choice on the previous page" in new LocalSetup {
      lazy val sessionCacheResponse = Some(CacheMap("id", Map("soleResidencyChoiceDto" -> Json.toJson(ResidencyChoiceDto(SoleAddrType)))))

      val r = controller.showPostcodeLookupForm(SoleAddrType)(buildAddressRequest("GET"))

      status(r) shouldBe OK
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
    }

    "return 200 if the user is on correspondence address journey and has postal address type" in new LocalSetup {
      lazy val sessionCacheResponse = Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      val r = controller.showPostcodeLookupForm(PostalAddrType)(buildAddressRequest("GET"))

      status(r) shouldBe OK
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
    }

    "redirect to the beginning of the journey when user has not indicated Residency choice on previous page" in new LocalSetup {
      lazy val sessionCacheResponse = None

      val r = controller.showPostcodeLookupForm(SoleAddrType)(buildAddressRequest("GET"))

      status(r) shouldBe SEE_OTHER
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
      redirectLocation(await(r)) shouldBe Some("/personal-account/personal-details")
    }

    "redirect to the beginning of the journey when user has not visited your-address page on correspondence journey" in new LocalSetup {
      lazy val sessionCacheResponse = None

      val r = controller.showPostcodeLookupForm(PostalAddrType)(buildAddressRequest("GET"))

      status(r) shouldBe SEE_OTHER
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
      redirectLocation(await(r)) shouldBe Some("/personal-account/personal-details")
    }

  }


  "Calling AddressController.processPostcodeLookupForm" should {

    trait LocalSetup extends WithAddressControllerSpecSetup {
      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(personDetails)
      override lazy val sessionCacheResponse = Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"
    }

    "redirect to the select address page with the correct postcode" in new LocalSetup {
      val r = controller.processPostcodeLookupForm(PostalAddrType)(buildAddressRequest("POST").withFormUrlEncodedBody("postcode" -> " AA1 1AA "))

      status(r) shouldBe SEE_OTHER
      verify(controller.sessionCache, times(1)).cache(meq("postalAddressFinderDto"), meq(AddressFinderDto("AA1 1AA", None)))(any(), any(), any())
      redirectLocation(await(r)) shouldBe Some("/personal-account/your-address/postal/select-address?postcode=AA1+1AA")
    }

    "redirect to the select address page with the correct postcode and filter when one is supplied" in new LocalSetup {
      val r = controller.processPostcodeLookupForm(PostalAddrType)(buildAddressRequest("POST").withFormUrlEncodedBody("postcode" -> "AA1 1AA", "filter" -> "16"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/your-address/postal/select-address?postcode=AA1+1AA&filter=16")
    }

    "return a 400 status when given a badly formatted postcode" in new LocalSetup {
      val r = controller.processPostcodeLookupForm(PostalAddrType)(buildAddressRequest("POST").withFormUrlEncodedBody("postcode" -> "BADPOSTCODE"))

      status(r) shouldBe BAD_REQUEST
    }

  }


  "Calling AddressController.showAddressSelectorForm" should {

    trait LocalSetup extends WithAddressControllerSpecSetup {

      def addressLookupResponse: AddressLookupResponse

      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(personDetails)
      override lazy val sessionCacheResponse = Some(CacheMap("id", Map("postalAddressFinderDto" -> Json.toJson(AddressFinderDto("AA1 1AA", None)))))
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"

      def comparatorDataEvent(dataEvent: DataEvent, auditType: String, postcode: String): DataEvent = DataEvent(
        "pertax-frontend", auditType, dataEvent.eventId,
        Map("path" -> "/test", "transactionName" -> "find_address"),
        Map("nino" -> Fixtures.fakeNino.nino, "postcode" -> postcode),
        dataEvent.generatedAt
      )

      lazy val c1 = {
        when(controller.addressLookupService.lookup(meq("AA1 1AA"), any())(any())) thenReturn {
          Future.successful(addressLookupResponse)
        }
        controller
      }

    }

    "return 404 and log a addressLookupNotFound audit event when an empty recordset is returned by the address lookup service" in new LocalSetup {
      override lazy val addressLookupResponse = AddressLookupSuccessResponse(RecordSet(List()))

      val r = c1.showAddressSelectorForm(PostalAddrType, "AA1 1AA", None)(buildAddressRequest("GET"))

      status(r) shouldBe NOT_FOUND
      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(c1.auditConnector, times(1)).sendEvent(eventCaptor.capture())(any(), any())
      pruneDataEvent(eventCaptor.getValue) shouldBe comparatorDataEvent(eventCaptor.getValue, "addressLookupNotFound", "AA1 1AA")
      verify(c1.sessionCache, times(1)).fetch()(any(), any())
    }

    "redirect to the edit address page for a postal address type and log a addressLookupResults audit event when a single record is returned by the address lookup service" in new LocalSetup {
      override lazy val addressLookupResponse = AddressLookupSuccessResponse(RecordSet(List(fakeStreetPafAddressRecord)))

      val r = c1.showAddressSelectorForm(PostalAddrType, "AA1 1AA", None)(buildAddressRequest("GET"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/your-address/postal/edit-address")
      verify(c1.sessionCache, times(1)).cache(meq("postalSelectedAddressRecord"), meq(fakeStreetPafAddressRecord))(any(), any(), any())
      verify(c1.sessionCache, times(1)).fetch()(any(), any())
      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(c1.auditConnector, times(1)).sendEvent(eventCaptor.capture())(any(), any())
      pruneDataEvent(eventCaptor.getValue)  shouldBe comparatorDataEvent(eventCaptor.getValue, "addressLookupResults", "AA1 1AA")
    }

    "redirect to the edit-address page for a non postal address type and log a addressLookupResults audit event when a single record is returned by the address lookup service" in new LocalSetup {
      override lazy val addressLookupResponse = AddressLookupSuccessResponse(RecordSet(List(fakeStreetPafAddressRecord)))
      override lazy val sessionCacheResponse = Some(CacheMap("id", Map("soleAddressFinderDto" -> Json.toJson(AddressFinderDto("AA1 1AA", None)))))

      val r = c1.showAddressSelectorForm(SoleAddrType, "AA1 1AA", None)(buildAddressRequest("GET"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/your-address/sole/edit-address")
      verify(c1.sessionCache, times(1)).cache(meq("soleSelectedAddressRecord"), meq(fakeStreetPafAddressRecord))(any(), any(), any())
      verify(c1.sessionCache, times(1)).fetch()(any(), any())
      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(c1.auditConnector, times(1)).sendEvent(eventCaptor.capture())(any(), any())
      pruneDataEvent(eventCaptor.getValue)  shouldBe comparatorDataEvent(eventCaptor.getValue, "addressLookupResults", "AA1 1AA")
    }

    "return 200 and log a addressLookupResults audit event when multiple records are returned by the address lookup service" in new LocalSetup {
      override lazy val addressLookupResponse = AddressLookupSuccessResponse(oneAndTwoOtherPlacePafRecordSet)

      val r = c1.showAddressSelectorForm(PostalAddrType, "AA1 1AA", None)(buildAddressRequest("GET"))

      status(r) shouldBe OK
      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(c1.auditConnector, times(1)).sendEvent(eventCaptor.capture())(any(), any())
      verify(c1.sessionCache, times(1)).fetch()(any(), any())
      pruneDataEvent(eventCaptor.getValue) shouldBe comparatorDataEvent(eventCaptor.getValue, "addressLookupResults", "AA1 1AA")
    }

    "return Not Found when an empty recordset is returned by the address lookup service and back = true" in new LocalSetup {
      override lazy val addressLookupResponse = AddressLookupSuccessResponse(RecordSet(List()))
      override lazy val sessionCacheResponse = Some(CacheMap("id",
        Map("postalAddressFinderDto" -> Json.toJson(AddressFinderDto("AA1 1AA", None)),
          "addressLookupServiceDown" -> Json.toJson(Some(false))
        )))

      val r = c1.showAddressSelectorForm(PostalAddrType, "AA1 1AA", None, Some(true))(buildAddressRequest("GET"))

      status(r) shouldBe NOT_FOUND
      verify(c1.sessionCache, times(0)).cache(any(), any())(any(), any(), any())
      verify(c1.sessionCache, times(1)).fetch()(any(), any())
    }

    "redirect to the postcodeLookupForm and when a single record is returned by the address lookup service and back = true" in new LocalSetup {
      override lazy val addressLookupResponse = AddressLookupSuccessResponse(RecordSet(List(fakeStreetPafAddressRecord)))

      val r = c1.showAddressSelectorForm(PostalAddrType, "AA1 1AA", None, Some(true))(buildAddressRequest("GET"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/your-address/postal/find-address")
      verify(c1.sessionCache, times(1)).fetch()(any(), any())
      verify(c1.sessionCache, times(0)).cache(any(), any())(any(), any(), any())
    }

    "return 200 and display the select-address page when multiple records are returned by the address lookup service back=true" in new LocalSetup {
      override lazy val addressLookupResponse = AddressLookupSuccessResponse(oneAndTwoOtherPlacePafRecordSet)

      val r = c1.showAddressSelectorForm(PostalAddrType, "AA1 1AA", None, Some(true))(buildAddressRequest("GET"))

      status(r) shouldBe OK
      verify(c1.sessionCache, times(1)).fetch()(any(), any())
      verify(c1.sessionCache, times(0)).cache(any(), any())(any(), any(), any())
    }

  }


  "Calling AddressController.processAddressSelectorForm" should {

    trait LocalSetup extends WithAddressControllerSpecSetup {
      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(personDetails)
      override lazy val sessionCacheResponse = Some(CacheMap("id", Map("addressLookupServiceDown" -> Json.toJson(Some(false)))))
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"

      val addressLookupResponse = AddressLookupSuccessResponse(oneAndTwoOtherPlacePafRecordSet)

      lazy val c1 = {
        when(controller.addressLookupService.lookup(meq("AA1 1AA"), any())(any())) thenReturn {
          Future.successful(addressLookupResponse)
        }
        controller
      }

    }

    "call the address lookup service and return 400 when supplied no addressId in the form" in new LocalSetup {
      val r = c1.processAddressSelectorForm(PostalAddrType, "AA1 1AA", None)(buildAddressRequest("GET"))

      status(r) shouldBe BAD_REQUEST
      verify(c1.sessionCache, times(1)).fetch()(any(), any())
    }

    "call the address lookup service and redirect to the edit address form for a postal address type when supplied with an addressId" in new LocalSetup {
      val r = c1.processAddressSelectorForm(PostalAddrType, "AA1 1AA", None)(buildAddressRequest("GET").withFormUrlEncodedBody("addressId" -> " GB990091234514 "))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/your-address/postal/edit-address")
      verify(c1.sessionCache, times(1)).cache(meq("postalSelectedAddressRecord"), meq(oneOtherPlacePafAddressRecord))(any(), any(), any())
      verify(c1.sessionCache, times(1)).fetch()(any(), any())
    }

    "call the address lookup service and redirect to the enter start date form for a non postal address type when supplied with an addressId" in new LocalSetup {
      val cacheAddress =  AddressDto.fromAddressRecord(oneOtherPlacePafAddressRecord)

      val r = c1.processAddressSelectorForm(SoleAddrType, "AA1 1AA", None)(buildAddressRequest("GET").withFormUrlEncodedBody("addressId" -> "GB990091234514"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/your-address/sole/enter-start-date")
      verify(c1.sessionCache, times(1)).cache(meq("soleSubmittedAddressDto"), meq(cacheAddress))(any(), any(), any())
      verify(c1.sessionCache, times(1)).fetch()(any(), any())
    }

    "call the address lookup service and return a 500 when an invalid addressId is supplied in the form" in new LocalSetup {
      val r = c1.processAddressSelectorForm(PostalAddrType, "AA1 1AA", None)(buildAddressRequest("GET").withFormUrlEncodedBody("addressId" -> "GB000000000000"))

      status(r) shouldBe INTERNAL_SERVER_ERROR
      verify(c1.sessionCache, times(0)).cache(any(), any())(any(), any(), any())
      verify(c1.sessionCache, times(1)).fetch()(any(), any())
    }

  }


  "Calling AddressController.showUpdateAddressForm" should {

    trait LocalSetup extends WithAddressControllerSpecSetup {
      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(personDetails)
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"
    }

    "find only the selected address from the session cache and no residency choice and return 303" in new LocalSetup {
      lazy val sessionCacheResponse = Some(CacheMap("id", Map("soleSelectedAddressRecord" -> Json.toJson(fakeStreetPafAddressRecord))))

      val r = controller.showUpdateAddressForm(SoleAddrType)(buildAddressRequest("GET"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/personal-details")
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
    }

    "fetch the selected address and a sole residencyChoice has been selected from the session cache and return 200" in new LocalSetup {
      lazy val sessionCacheResponse = Some(CacheMap("id", Map("selectedAddressRecord" -> Json.toJson(fakeStreetPafAddressRecord),
        "soleResidencyChoiceDto" -> Json.toJson(ResidencyChoiceDto(PostalAddrType)))))

      val r = controller.showUpdateAddressForm(SoleAddrType)(buildAddressRequest("GET"))

      status(r) shouldBe OK
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
    }

    "find no selected address with sole address type but residencyChoice in the session cache and still return 200" in new LocalSetup {
      lazy val sessionCacheResponse = Some(CacheMap("id", Map("soleResidencyChoiceDto" -> Json.toJson(ResidencyChoiceDto(SoleAddrType)))))

      val r = controller.showUpdateAddressForm(SoleAddrType)(buildAddressRequest("GET"))

      status(r) shouldBe OK
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
    }

    "find no residency choice in the session cache and redirect to the beginning of the journey" in new LocalSetup {
      lazy val sessionCacheResponse = Some(CacheMap("id", Map.empty))

      val r = controller.showUpdateAddressForm(SoleAddrType)(buildAddressRequest("GET"))

      status(r) shouldBe SEE_OTHER
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
      redirectLocation(await(r)) shouldBe Some("/personal-account/personal-details")
    }

    "redirect user to beginning of journey and return 303 for postal addressType and no pagevisitedDto in cache" in new LocalSetup {
      lazy val sessionCacheResponse = None

      val r = controller.showUpdateAddressForm(PostalAddrType)(buildAddressRequest("GET"))

      status(r) shouldBe SEE_OTHER
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
      redirectLocation(await(r)) shouldBe Some("/personal-account/personal-details")
    }

    "display edit address page and return 200 for postal addressType with pagevisitedDto and addressRecord in cache" in new LocalSetup {
      lazy val sessionCacheResponse = Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)),
        "selectedAddressRecord" -> Json.toJson(fakeStreetPafAddressRecord))))

      val r = controller.showUpdateAddressForm(PostalAddrType)(buildAddressRequest("GET"))

      status(r) shouldBe OK
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
    }

    "display edit address page and return 200 for postal addressType with pagevisitedDto and no addressRecord in cache" in new LocalSetup {
      lazy val sessionCacheResponse = Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      val r = controller.showUpdateAddressForm(PostalAddrType)(buildAddressRequest("GET"))

      status(r)shouldBe OK
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
    }

    "find no addresses in the session cache and return 303" in new LocalSetup {
      lazy val sessionCacheResponse = Some(CacheMap("id", Map.empty))

      val r = controller.showUpdateAddressForm(PostalAddrType)(buildAddressRequest("GET"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/personal-details")
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
    }

    "find sole selected and submitted addresses in the session cache and return 200" in new LocalSetup {
      lazy val sessionCacheResponse = Some(CacheMap("id", Map("soleSelectedAddressRecord" -> Json.toJson(fakeStreetPafAddressRecord),
        "soleSubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)),
        "soleResidencyChoiceDto" -> Json.toJson(ResidencyChoiceDto(SoleAddrType)))))

      val r = controller.showUpdateAddressForm(SoleAddrType)(buildAddressRequest("GET"))

      status(r) shouldBe OK
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
    }

    "find no selected address but a submitted address in the session cache and return 200" in new LocalSetup {
      lazy val sessionCacheResponse = Some(CacheMap("id", Map("soleSubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)),
        "soleResidencyChoiceDto" -> Json.toJson(ResidencyChoiceDto(SoleAddrType)))))

      val r = controller.showUpdateAddressForm(SoleAddrType)(buildAddressRequest("GET"))

      status(r) shouldBe OK
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
    }

    "show 'Enter the address' when user amends correspondence address manually and address has not been selected" in new LocalSetup {
      lazy val sessionCacheResponse = Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      val r = controller.showUpdateAddressForm(PostalAddrType)(buildAddressRequest("GET"))

      status(r)shouldBe OK
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
      val doc = Jsoup.parse(contentAsString(r))
      doc.getElementsByClass("heading-xlarge").toString().contains("Enter the address") shouldBe true
    }

    "show 'Enter your address' when user amends residential address manually and address has not been selected" in new LocalSetup {
      lazy val sessionCacheResponse = Some(CacheMap("id", Map("soleResidencyChoiceDto" -> Json.toJson(ResidencyChoiceDto(SoleAddrType)))))

      val r = controller.showUpdateAddressForm(SoleAddrType)(buildAddressRequest("GET"))

      status(r)shouldBe OK
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
      val doc = Jsoup.parse(contentAsString(r))
      doc.getElementsByClass("heading-xlarge").toString().contains("Enter your address") shouldBe true
    }

    "show 'Edit the address (optional)' when user amends correspondence address manually and address has been selected" in new LocalSetup {
      lazy val sessionCacheResponse = Some(CacheMap("id", Map(
        "addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)),
        "postalSelectedAddressRecord" -> Json.toJson(Fixtures.fakeStreetPafAddressRecord)
      )))

      val r = controller.showUpdateAddressForm(PostalAddrType)(buildAddressRequest("GET"))

      status(r)shouldBe OK
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
      val doc = Jsoup.parse(contentAsString(r))
      doc.getElementsByClass("heading-xlarge").toString().contains("Edit the address (optional)") shouldBe true
    }

    "show 'Edit your address (optional)' when user amends residential address manually and address has been selected" in new LocalSetup {
      lazy val sessionCacheResponse = Some(CacheMap("id", Map(
        "soleResidencyChoiceDto" -> Json.toJson(ResidencyChoiceDto(SoleAddrType)),
        "soleSelectedAddressRecord" -> Json.toJson(Fixtures.fakeStreetPafAddressRecord)
      )))

      val r = controller.showUpdateAddressForm(SoleAddrType)(buildAddressRequest("GET"))

      status(r) shouldBe OK
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
      val doc = Jsoup.parse(contentAsString(r))
      doc.getElementsByClass("heading-xlarge").toString().contains("Edit your address (optional)") shouldBe true
    }

  }


  "Calling AddressController.processUpdateAddressForm" should {

    trait LocalSetup extends WithAddressControllerSpecSetup {
      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(personDetails)
      override lazy val sessionCacheResponse = Some(CacheMap("id", Map("addressLookupServiceDown" -> Json.toJson(Some(true)))))
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"
    }

    "return 400 when supplied invalid form input" in new LocalSetup {
      override lazy val sessionCacheResponse = Some(CacheMap("id", Map("selectedAddressRecord" -> Json.toJson(""))))

      val r = controller.processUpdateAddressForm(PostalAddrType)(buildAddressRequest("POST"))

      status(r) shouldBe BAD_REQUEST
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
    }

    "return 303, caching addressDto and redirecting to enter start date page when supplied valid form input on a postal journey" in new LocalSetup {
      val r = controller.processUpdateAddressForm(PostalAddrType)(buildAddressRequest("POST").withFormUrlEncodedBody(fakeStreetTupleListAddressForUnmodified : _*))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/your-address/postal/changes")
      verify(controller.sessionCache, times(1)).cache(meq("postalSubmittedAddressDto"), meq(asAddressDto(fakeStreetTupleListAddressForUnmodified)))(any(), any(), any())
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
    }



    "return 303, caching addressDto and redirecting to enter start date page when supplied valid form input on a non postal journey" in new LocalSetup {
      val r = controller.processUpdateAddressForm(SoleAddrType)(buildAddressRequest("POST").withFormUrlEncodedBody(fakeStreetTupleListAddressForUnmodified : _*))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/your-address/sole/enter-start-date")
      verify(controller.sessionCache, times(1)).cache(meq("soleSubmittedAddressDto"), meq(asAddressDto(fakeStreetTupleListAddressForUnmodified)))(any(), any(), any())
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
    }

  }


  "Calling AddressController.enterStartDate" should {

    trait LocalSetup extends WithAddressControllerSpecSetup {
      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(personDetails)
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"
    }

    "return 200 when passed PrimaryAddrType and submittedAddressDto is in keystore" in new LocalSetup {
      lazy val sessionCacheResponse = Some(CacheMap("id", Map("primarySubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)))))

      val r = controller.enterStartDate(PrimaryAddrType)(buildAddressRequest("GET"))

      status(r) shouldBe OK
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
    }

    "return 200 when passed SoleAddrType and submittedAddressDto is in keystore" in new LocalSetup {
      lazy val sessionCacheResponse = Some(CacheMap("id", Map("soleSubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)))))

      val r = controller.enterStartDate(SoleAddrType)(buildAddressRequest("GET"))

      status(r) shouldBe OK
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
    }

    "return 404 when passed PostalAddrType regardless of keystore" in new LocalSetup {
      lazy val sessionCacheResponse = Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      val r = controller.enterStartDate(PostalAddrType)(buildAddressRequest("GET"))

      status(r) shouldBe NOT_FOUND
      verify(controller.sessionCache, times(0)).fetch()(any(), any())
    }

    "redirect back to start of journey if submittedAddressDto is missing from keystore" in new LocalSetup {
      lazy val sessionCacheResponse = Some(CacheMap("id", Map.empty))

      val r = controller.enterStartDate(PrimaryAddrType)(buildAddressRequest("GET"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/personal-details")
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
    }

  }


  "Calling AddressController.processEnterStartDate" should {

    trait LocalSetup extends WithAddressControllerSpecSetup {
      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(personDetails)
      override lazy val sessionCacheResponse = Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"
    }

    "return 303 when passed PrimaryAddrType and a valid form with low numbers" in new LocalSetup {
      val r = controller.processEnterStartDate(PrimaryAddrType)(buildAddressRequest("POST").withFormUrlEncodedBody("startDate.day" -> "1", "startDate.month" -> "1", "startDate.year" -> "2016"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/your-address/primary/changes")
      verify(controller.sessionCache, times(1)).cache(meq("primarySubmittedStartDateDto"), meq(DateDto.build(1, 1, 2016)))(any(), any(), any())
    }

    "return 303 when passed PrimaryAddrType and date is in the today" in new LocalSetup {

      val r = controller.processEnterStartDate(PrimaryAddrType)(buildAddressRequest("POST").withFormUrlEncodedBody("startDate.day" -> "2", "startDate.month" -> "2", "startDate.year" -> "2016"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/your-address/primary/changes")
      verify(controller.sessionCache, times(1)).cache(meq("primarySubmittedStartDateDto"), meq(DateDto.build(2, 2, 2016)))(any(), any(), any())
    }

    "redirect to the changes to sole address page when passed PrimaryAddrType and a valid form with high numbers" in new LocalSetup {
      val r = controller.processEnterStartDate(SoleAddrType)(buildAddressRequest("POST").withFormUrlEncodedBody("startDate.day" -> "31", "startDate.month" -> "12", "startDate.year" -> thisYearStr))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/your-address/sole/changes")
      verify(controller.sessionCache, times(1)).cache(meq("soleSubmittedStartDateDto"), meq(DateDto.build(31, 12, 2015)))(any(), any(), any())
    }

    "return 400 when passed PrimaryAddrType and day out of range" in new LocalSetup {
      status(controller.processEnterStartDate(PrimaryAddrType)(buildAddressRequest("POST").withFormUrlEncodedBody("startDate.day" -> "0", "startDate.month" -> "1", "startDate.year" -> thisYearStr))) shouldBe BAD_REQUEST
      status(controller.processEnterStartDate(PrimaryAddrType)(buildAddressRequest("POST").withFormUrlEncodedBody("startDate.day" -> "32", "startDate.month" -> "1", "startDate.year" -> thisYearStr))) shouldBe BAD_REQUEST
      verify(controller.sessionCache, times(0)).cache(any(), any())(any(), any(), any())
    }

    "return 400 when passed PrimaryAddrType and month out of range" in new LocalSetup {
      status(controller.processEnterStartDate(PrimaryAddrType)(buildAddressRequest("POST").withFormUrlEncodedBody("startDate.day" -> "1", "startDate.month" -> "0", "startDate.year" -> thisYearStr))) shouldBe BAD_REQUEST
      status(controller.processEnterStartDate(PrimaryAddrType)(buildAddressRequest("POST").withFormUrlEncodedBody("startDate.day" -> "31", "startDate.month" -> "13", "startDate.year" -> thisYearStr))) shouldBe BAD_REQUEST
      verify(controller.sessionCache, times(0)).cache(any(), any())(any(), any(), any())
    }

    "return 400 when passed PrimaryAddrType and date is in the future" in new LocalSetup {
      status(controller.processEnterStartDate(PrimaryAddrType)(buildAddressRequest("POST").withFormUrlEncodedBody("startDate.day" -> "3", "startDate.month" -> "2", "startDate.year" -> "2016"))) shouldBe BAD_REQUEST
      verify(controller.sessionCache, times(0)).cache(any(), any())(any(), any(), any())
    }

    "return a 400 when startDate is earlier than recorded with sole address type" in new LocalSetup {
      val r = controller.processEnterStartDate(SoleAddrType)(buildAddressRequest("POST").withFormUrlEncodedBody("startDate.day" -> "14", "startDate.month" -> "03", "startDate.year" -> "2015"))

      status(r) shouldBe BAD_REQUEST
    }

    "return a 400 when startDate is the same as recorded with sole address type" in new LocalSetup {
      val r = controller.processEnterStartDate(SoleAddrType)(buildAddressRequest("POST").withFormUrlEncodedBody("startDate.day" -> "15", "startDate.month" -> "03", "startDate.year" -> "2015"))

      status(r) shouldBe BAD_REQUEST
    }

    "return a 400 when startDate is earlier than recorded with primary address type" in new LocalSetup {
      val r = controller.processEnterStartDate(PrimaryAddrType)(buildAddressRequest("POST").withFormUrlEncodedBody("startDate.day" -> "14", "startDate.month" -> "03", "startDate.year" -> "2015"))

      status(r) shouldBe BAD_REQUEST
    }

    "return a 400 when startDate is the same as recorded with primary address type" in new LocalSetup {
      val r = controller.processEnterStartDate(PrimaryAddrType)(buildAddressRequest("POST").withFormUrlEncodedBody("startDate.day" -> "15", "startDate.month" -> "03", "startDate.year" -> "2015"))

      status(r) shouldBe BAD_REQUEST
    }

    "redirect to correct successful url when supplied with startDate after recorded with sole address type" in new LocalSetup {
      val r = controller.processEnterStartDate(SoleAddrType)(buildAddressRequest("POST").withFormUrlEncodedBody("startDate.day" -> "16", "startDate.month" -> "03", "startDate.year" -> thisYearStr))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/your-address/sole/changes")
    }

    "redirect to correct successful url when supplied with startDate after startDate on record with primary address" in new LocalSetup {
      val r = controller.processEnterStartDate(PrimaryAddrType)(buildAddressRequest("POST").withFormUrlEncodedBody("startDate.day" -> "20", "startDate.month" -> "06", "startDate.year" -> thisYearStr))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/your-address/primary/changes")
    }

    "redirect to success page when no startDate is on record" in new LocalSetup {
      lazy val personDetailsNoStartDate = personDetails.copy(address = personDetails.address.map(_.copy(startDate = None)))
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(personDetailsNoStartDate)

      val r = controller.processEnterStartDate(PrimaryAddrType)(buildAddressRequest("POST").withFormUrlEncodedBody("startDate.day" -> "20", "startDate.month" -> "06", "startDate.year" -> thisYearStr))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/your-address/primary/changes")
    }

    "redirect to success page when no address is on record" in new LocalSetup {
      lazy val personDetailsNoAddress = personDetails.copy(address = None)
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(personDetailsNoAddress)

      val r = controller.processEnterStartDate(PrimaryAddrType)(buildAddressRequest("POST").withFormUrlEncodedBody("startDate.day" -> "20", "startDate.month" -> "06", "startDate.year" -> thisYearStr))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/your-address/primary/changes")
    }

  }


  "Calling AddressController.reviewChanges" should {

    trait LocalSetup extends WithAddressControllerSpecSetup {
      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(personDetails)
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"
    }

    "return 200 if required data is present in keystore" in new LocalSetup {
      lazy val sessionCacheResponse = Some(CacheMap("id", Map(
        "primarySubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified))
      )))

      val r = controller.reviewChanges(PrimaryAddrType)(buildAddressRequest("GET"))

      status(r) shouldBe OK
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
    }


    "redirect back to start of journey if selectedAddressRecord is missing from keystore" in new LocalSetup {
      lazy val sessionCacheResponse = Some(CacheMap("id", Map("soleSubmittedStartDateDto" -> Json.toJson(DateDto.build(1, 1, 2016)))))

      val r = controller.reviewChanges(SoleAddrType)(buildAddressRequest("GET"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/personal-details")
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
    }

  }


  "Calling AddressController.submitChanges" should {

    trait LocalSetup extends WithAddressControllerSpecSetup {
      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(personDetails)
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"

      def comparatorDataEvent(dataEvent: DataEvent, auditType: String, uprn: Option[String], includeOriginals: Boolean, submittedLine1: Option[String] = Some("1 Fake Street"), addressType: Option[String] = Some("Residential")) = DataEvent(
        "pertax-frontend", auditType, dataEvent.eventId,
        Map("path" -> "/test", "transactionName" -> "change_of_address"),
        Map(
          "nino" -> Some(Fixtures.fakeNino.nino),
          "etag" -> Some("115"),
          "submittedLine1"    -> submittedLine1,
          "submittedLine2"    -> Some("Fake Town"),
          "submittedLine3"    -> Some("Fake City"),
          "submittedLine4"    -> Some("Fake Region"),
          "submittedPostcode" -> Some("AA1 1AA"),
          "addressType"       -> addressType,
          "submittedUPRN"     -> uprn,
          "originalLine1"     -> Some("1 Fake Street").filter(x => includeOriginals),
          "originalLine2"     -> Some("Fake Town").filter(x => includeOriginals),
          "originalLine3"     -> Some("Fake City").filter(x => includeOriginals),
          "originalLine4"     -> Some("Fake Region").filter(x => includeOriginals),
          "originalPostcode"  -> Some("AA1 1AA").filter(x => includeOriginals),
          "originalUPRN"      -> uprn.filter(x => includeOriginals)
        ).map(t => t._2.map( (t._1, _) )).flatten.toMap,
        dataEvent.generatedAt
      )

    }

    "redirect to start of journey if primarySubmittedAddressDto is missing from the cache" in new LocalSetup {
      override lazy val sessionCacheResponse = Some(CacheMap("id", Map(
        "primarySubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
      )))

      val r = controller.submitChanges(PrimaryAddrType)(buildAddressRequest("POST"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/personal-details")

      verify(controller.auditConnector, times(0)).sendEvent(any())(any(), any())
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
    }

    "render the thank-you page and log a postcodeAddressSubmitted audit event upon successful submission of an unmodified address" in new LocalSetup {
      override lazy val sessionCacheResponse = Some(CacheMap("id", Map(
        "primarySelectedAddressRecord" -> Json.toJson(fakeStreetPafAddressRecord),
        "primarySubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)),
        "primarySubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
      )))

      val r = controller.submitChanges(PrimaryAddrType)(buildAddressRequest("POST"))

      status(r) shouldBe OK
      val arg = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(controller.auditConnector, times(1)).sendEvent(arg.capture())(any(), any())
      val dataEvent = arg.getValue
      pruneDataEvent(dataEvent) shouldBe comparatorDataEvent(dataEvent, "postcodeAddressSubmitted", Some("GB101"), false)
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
      verify(controller.citizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), meq(fakeAddress))(any())
    }

    "render the thank you page and log a postcodeAddressSubmitted audit event upon successful submission of an unmodified address, this time using postal type and having no postalSubmittedStartDateDto in the cache " in new LocalSetup {
      override lazy val fakeAddress = buildFakeAddress.copy(`type` = Some("Correspondence"), startDate = Some(LocalDate.now))
      override lazy val sessionCacheResponse = Some(CacheMap("id", Map(
        "postalSelectedAddressRecord" -> Json.toJson(fakeStreetPafAddressRecord),
        "postalSubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified))
      )))

      val r = controller.submitChanges(PostalAddrType)(buildAddressRequest("POST").withFormUrlEncodedBody(fakeStreetTupleListAddressForUnmodified : _*))

      status(r) shouldBe OK
      val arg = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(controller.auditConnector, times(1)).sendEvent(arg.capture())(any(), any())
      val dataEvent = arg.getValue
      pruneDataEvent(dataEvent) shouldBe comparatorDataEvent(dataEvent, "postcodeAddressSubmitted", Some("GB101"), false, addressType = Some("Correspondence"))
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
      verify(controller.citizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), meq(fakeAddress))(any())
    }

    "render the thank you page and log a manualAddressSubmitted audit event upon successful submission of a manually entered address" in new LocalSetup {
      override lazy val sessionCacheResponse = Some(CacheMap("id", Map(
        "primarySubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForManualyEntered)),
        "primarySubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
      )))

      val r = controller.submitChanges(PrimaryAddrType)(buildAddressRequest("POST"))

      status(r) shouldBe OK
      val arg = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(controller.auditConnector, times(1)).sendEvent(arg.capture())(any(), any())
      val dataEvent = arg.getValue
      pruneDataEvent(dataEvent) shouldBe comparatorDataEvent(dataEvent, "manualAddressSubmitted", None, false)
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
      verify(controller.citizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), meq(fakeAddress))(any())
    }

    "render the thank you page and log a postcodeAddressModifiedSubmitted audit event upon successful of a modified address" in new LocalSetup {
      override lazy val fakeAddress = buildFakeAddress.copy(line1 = Some("11 Fake Street"))
      override lazy val sessionCacheResponse = Some(CacheMap("id", Map(
        "primarySelectedAddressRecord" -> Json.toJson(fakeStreetPafAddressRecord),
        "primarySubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForModified)),
        "primarySubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
      )))

      val r = controller.submitChanges(PrimaryAddrType)(buildAddressRequest("POST"))

      status(r) shouldBe OK
      val arg = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(controller.auditConnector, times(1)).sendEvent(arg.capture())(any(), any())
      val dataEvent = arg.getValue
      pruneDataEvent(dataEvent) shouldBe comparatorDataEvent(dataEvent, "postcodeAddressModifiedSubmitted", Some("GB101"), true, Some("11 Fake Street"))
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
      verify(controller.citizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), meq(fakeAddress))(any())
    }

    "return 400 if UpdateAddressBadRequestResponse is received from citizen-details" in new LocalSetup {
      override lazy val sessionCacheResponse = Some(CacheMap("id", Map(
        "primarySubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)),
        "primarySubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
      )))
      override lazy val updateAddressResponse = UpdateAddressBadRequestResponse

      val r = controller.submitChanges(PrimaryAddrType)(buildAddressRequest("POST"))

      status(r) shouldBe BAD_REQUEST
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
      verify(controller.citizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), meq(fakeAddress))(any())
    }

    "return 500 if an UpdateAddressUnexpectedResponse is received from citizen-details" in new LocalSetup {
      override lazy val sessionCacheResponse = Some(CacheMap("id", Map(
        "primarySubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)),
        "primarySubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
      )))
      override lazy val updateAddressResponse = UpdateAddressUnexpectedResponse(HttpResponse(SEE_OTHER))

      val r = controller.submitChanges(PrimaryAddrType)(buildAddressRequest("POST"))

      status(r) shouldBe INTERNAL_SERVER_ERROR
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
      verify(controller.citizenDetailsService, times(1)).updateAddress(meq(Fixtures.fakeNino), meq("115"), meq(buildFakeAddress))(any())
    }

    "return 500 if an UpdateAddressErrorResponse is received from citizen-details" in new LocalSetup {
      override lazy val sessionCacheResponse = Some(CacheMap("id", Map(
        "primarySubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)),
        "primarySubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
      )))
      override lazy val updateAddressResponse = UpdateAddressErrorResponse(new RuntimeException("Any exception"))

      val r = controller.submitChanges(PrimaryAddrType)(buildAddressRequest("POST"))

      status(r) shouldBe INTERNAL_SERVER_ERROR
      verify(controller.sessionCache, times(1)).fetch()(any(),any())
      verify(controller.citizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), meq(fakeAddress))(any())
    }

  }


  "Calling AddressController.showAddressAlreadyUpdated" should {

    trait LocalSetup extends WithAddressControllerSpecSetup {
      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(personDetails)
      override lazy val sessionCacheResponse = Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"
    }

    "display the showAddressAlreadyUpdated page" in new LocalSetup {
      val r = controller.showAddressAlreadyUpdated(PostalAddrType)(buildAddressRequest("GET"))

      status(r) shouldBe OK
    }

  }


  "Calling AddressController.lookingUpAddress" should {

    trait LookingUpAddressLocalSetup extends WithAddressControllerSpecSetup with Results {

      def addressLookupResponse: AddressLookupResponse

      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(personDetails)
      override lazy val sessionCacheResponse = Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"

      implicit lazy val context = PertaxContext(FakeRequest(), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(PertaxUser(buildFakeAuthContext(),
        UserDetails(UserDetails.GovernmentGatewayAuthProvider),
        None, true)))

      lazy val c1 = {
        when(controller.addressLookupService.lookup(meq("AA1 1AA"), any())(any())) thenReturn {
          Future.successful(addressLookupResponse)
        }
        controller
      }

      lazy val r = c1.lookingUpAddress(SoleAddrType, "AA1 1AA", AddressJourneyData(None, None, None, None, None, None, false)) {
        case AddressLookupSuccessResponse(_) => Future.successful(Ok("OK"))
      }

      val validAddressRecordSet = RecordSet(
        List(
          AddressRecord("GB990091234514",Address(List("1 Fake Street", "Fake Town"),Some("Fake City"), None,"AA1 1AA",Country("UK","United Kingdom")),"en"),
          AddressRecord("GB990091234515",Address(List("2 Fake Street", "Fake Town"),Some("Fake City"), None,"AA1 1AA",Country("UK","United Kingdom")),"en")
        )
      )

    }

    "redirect to 'Edit your address' when address lookup service is down" in new LookingUpAddressLocalSetup {
      override lazy val addressLookupResponse = AddressLookupErrorResponse(new RuntimeException("Some error"))

      status(r) shouldBe SEE_OTHER
      redirectLocation(await(r)) shouldBe Some("/personal-account/your-address/sole/edit-address")
    }

    "return valid recordset when address lookup service is up" in new LookingUpAddressLocalSetup {
      override lazy val addressLookupResponse = AddressLookupSuccessResponse(validAddressRecordSet)

      status(r) shouldBe OK
    }

  }
}
