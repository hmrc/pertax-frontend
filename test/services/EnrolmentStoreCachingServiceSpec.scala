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

package services

import models._
import org.mockito.Matchers._
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.Json
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.cache.client.CacheMap
import util.BaseSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EnrolmentStoreCachingServiceSpec extends BaseSpec with MockitoSugar with ScalaFutures {

  trait LocalSetup {

    val mockSessionCache: LocalSessionCache = mock[LocalSessionCache]

    val cacheResult: CacheMap = CacheMap("", Map.empty)
    val fetchResult: Option[SelfAssessmentUserType] = None

    lazy val sut: EnrolmentStoreCachingService = {

      val c = new EnrolmentStoreCachingService(mockSessionCache)

      when(
        mockSessionCache.cache[SelfAssessmentUserType](any(), any())(any(), any(), any())
      ) thenReturn Future.successful(cacheResult)

      when(
        mockSessionCache.fetchAndGetEntry[SelfAssessmentUserType](any())(any(), any(), any())
      ) thenReturn Future.successful(fetchResult)

      c
    }
  }

  val userTypeList: List[(SelfAssessmentUserType, String)] = List(
    (ActivatedOnlineFilerSelfAssessmentUser(SaUtr("111111111")), "an Activated SA user"),
    (NotYetActivatedOnlineFilerSelfAssessmentUser(SaUtr("111111111")), "a Not Yet Activated SA user"),
    (WrongCredentialsSelfAssessmentUser(SaUtr("111111111")), "a Wrong credentials SA user"),
    (NotEnrolledSelfAssessmentUser(SaUtr("111111111")), "a Not Enrolled SA user"),
    (NonFilerSelfAssessmentUser, "a Non Filer SA user")
  )

  "EnrolmentStoreCachingHelper" should {

    userTypeList.foreach {
      case (userType, key) =>
        s"add $key to the cache" in new LocalSetup {

          override val cacheResult: CacheMap =
            CacheMap("id", Map(SelfAssessmentUserType.cacheId -> Json.toJson(userType)))

          sut.addSaUserTypeToCache(userType).futureValue shouldBe cacheResult
        }

        s"fetch $key from the cache" in new LocalSetup {

          override val fetchResult: Option[SelfAssessmentUserType] = Some(userType)

          sut.getSaUserTypeFromCache().futureValue shouldBe fetchResult
        }
    }

    "return None when the cache is empty" in new LocalSetup {

      sut.getSaUserTypeFromCache().futureValue shouldBe None
    }

  }

}
