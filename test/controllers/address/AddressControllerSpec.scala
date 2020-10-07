/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers.address

import controllers.address
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import controllers.bindable.{PostalAddrType, PrimaryAddrType, SoleAddrType}
import models.{AddressJourneyTTLModel, EditCorrespondenceAddress, EditPrimaryAddress, EditSoleAddress}
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import play.api.mvc.Request
import play.api.mvc.Results._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import reactivemongo.bson.BSONDateTime
import uk.gov.hmrc.domain.{Generator, Nino}
import util.UserRequestFixture.buildUserRequest

import scala.concurrent.Future
import scala.util.Random

class AddressControllerSpec extends AddressBaseSpec {

  object SUT
      extends AddressController(
        injected[AuthJourney],
        withActiveTabAction,
        cc,
        displayAddressInterstitialView,
        mockEditAddressLockRepository
      )

  "addressJourneyEnforcer" should {

    "complete given block" when {

      "a nino and person details are present in the request" in {

        def userRequest[A]: UserRequest[A] =
          buildUserRequest(request = FakeRequest().asInstanceOf[Request[A]])

        val expectedContent = "Success"

        val result = SUT.addressJourneyEnforcer { _ => _ =>
          Ok(expectedContent)
        }(userRequest)

        status(result) shouldBe OK
        contentAsString(result) shouldBe expectedContent
      }
    }

    "show the address interstitial view page" when {

      "a nino cannot be found in the request" in {

        def userRequest[A]: UserRequest[A] =
          buildUserRequest(nino = None, request = FakeRequest().asInstanceOf[Request[A]])

        val result = SUT.addressJourneyEnforcer { _ => _ =>
          Ok("Success")
        }(userRequest)

        status(result) shouldBe OK
        contentAsString(result) should include(messages("label.you_can_see_this_part_of_your_account_if_you_complete"))
      }

      "person details cannot be found in the request" in {
        implicit def userRequest[A]: UserRequest[A] =
          buildUserRequest(personDetails = None, request = FakeRequest().asInstanceOf[Request[A]])

        val result = SUT.addressJourneyEnforcer { _ => _ =>
          Ok("Success")
        }

        status(result) shouldBe OK
        contentAsString(result) should include(messages("label.you_can_see_this_part_of_your_account_if_you_complete"))

      }
    }
  }
  "EditLockEnforcer" should {

    val expectedContent = "success"
    val testNino = Nino(new Generator(new Random()).nextNino.nino)
    def userRequest[A]: UserRequest[A] =
      buildUserRequest(nino = Some(testNino), request = FakeRequest().asInstanceOf[Request[A]])

    val correspondenceLock =
      AddressJourneyTTLModel(testNino.toString(), EditCorrespondenceAddress(BSONDateTime(11111111)))
    val soleLock = AddressJourneyTTLModel(testNino.toString(), EditSoleAddress(BSONDateTime(11111111)))
    val primaryLock = AddressJourneyTTLModel(testNino.toString(), EditPrimaryAddress(BSONDateTime(11111111)))

    "take user to PD page if Postal address and tile is present" in {

      when(mockEditAddressLockRepository.get(any())) thenReturn {
        Future.successful(List(correspondenceLock))
      }
      val result = SUT.lockedTileEnforcer(PostalAddrType) {
        Ok(expectedContent)
      }(userRequest)
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(address.routes.PersonalDetailsController.onPageLoad().url)
    }

    "complete block if Postal address and Postal Lock is not present" in {

      when(mockEditAddressLockRepository.get(any())) thenReturn {
        Future.successful(List())
      }
      val result = SUT.lockedTileEnforcer(PostalAddrType) {
        Ok(expectedContent)
      }(userRequest)
      status(result) shouldBe OK
      contentAsString(result) shouldBe expectedContent
    }

    "complete block if Postal address and SoleAddress is present" in {

      when(mockEditAddressLockRepository.get(any())) thenReturn {
        Future.successful(List(soleLock))
      }
      val result = SUT.lockedTileEnforcer(PostalAddrType) {
        Ok(expectedContent)
      }(userRequest)
      status(result) shouldBe OK
      contentAsString(result) shouldBe expectedContent
    }

    "complete block if Postal address and PrimaryAddress is present" in {

      when(mockEditAddressLockRepository.get(any())) thenReturn {
        Future.successful(List(primaryLock))
      }
      val result = SUT.lockedTileEnforcer(PostalAddrType) {
        Ok(expectedContent)
      }(userRequest)
      status(result) shouldBe OK
      contentAsString(result) shouldBe expectedContent
    }

    "take user to PersonalDetails page if Sole address and lock is present" in {

      when(mockEditAddressLockRepository.get(any())) thenReturn {
        Future.successful(List(soleLock))
      }
      val result = SUT.lockedTileEnforcer(SoleAddrType) {
        Ok(expectedContent)
      }(userRequest)
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(address.routes.PersonalDetailsController.onPageLoad().url)
    }

    "complete block if Sole address and SoleAddress Lock is not present" in {

      when(mockEditAddressLockRepository.get(any())) thenReturn {
        Future.successful(List())
      }
      val result = SUT.lockedTileEnforcer(SoleAddrType) {
        Ok(expectedContent)
      }(userRequest)
      status(result) shouldBe OK
      contentAsString(result) shouldBe expectedContent
    }

    "complete block if Sole address and Correspondence Lock is present" in {

      when(mockEditAddressLockRepository.get(any())) thenReturn {
        Future.successful(List(correspondenceLock))
      }
      val result = SUT.lockedTileEnforcer(SoleAddrType) {
        Ok(expectedContent)
      }(userRequest)
      status(result) shouldBe OK
      contentAsString(result) shouldBe expectedContent
    }

    "take user to Personal Details if Sole address and PrimaryAddress is present" in {

      when(mockEditAddressLockRepository.get(any())) thenReturn {
        Future.successful(List(primaryLock))
      }
      val result = SUT.lockedTileEnforcer(SoleAddrType) {
        Ok(expectedContent)
      }(userRequest)
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(address.routes.PersonalDetailsController.onPageLoad().url)
    }

    "take user to PersonalDetails page if Primary address and lock is present" in {

      when(mockEditAddressLockRepository.get(any())) thenReturn {
        Future.successful(List(soleLock))
      }
      val result = SUT.lockedTileEnforcer(PrimaryAddrType) {
        Ok(expectedContent)
      }(userRequest)
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(address.routes.PersonalDetailsController.onPageLoad().url)
    }

    "complete block if Primary address and Primary Lock is not present" in {

      when(mockEditAddressLockRepository.get(any())) thenReturn {
        Future.successful(List())
      }
      val result = SUT.lockedTileEnforcer(PrimaryAddrType) {
        Ok(expectedContent)
      }(userRequest)
      status(result) shouldBe OK
      contentAsString(result) shouldBe expectedContent
    }

    "complete block if Primary address and Correspondence Lock is present" in {

      when(mockEditAddressLockRepository.get(any())) thenReturn {
        Future.successful(List(correspondenceLock))
      }
      val result = SUT.lockedTileEnforcer(PrimaryAddrType) {
        Ok(expectedContent)
      }(userRequest)
      status(result) shouldBe OK
      contentAsString(result) shouldBe expectedContent
    }

    "take user to Personal Details if Primary address and Sole Address is present" in {

      when(mockEditAddressLockRepository.get(any())) thenReturn {
        Future.successful(List(soleLock))
      }
      val result = SUT.lockedTileEnforcer(PrimaryAddrType) {
        Ok(expectedContent)
      }(userRequest)
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(address.routes.PersonalDetailsController.onPageLoad().url)
    }
  }

}
