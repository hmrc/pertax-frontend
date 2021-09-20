/*
 * Copyright 2021 HM Revenue & Customs
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

package controllers.controllershelpers

import org.mockito.ArgumentMatchers.{any, eq => meq, _}
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.inject.bind
import play.api.libs.json.JsBoolean
import services.LocalSessionCache
import uk.gov.hmrc.http.cache.client.CacheMap
import util.BaseSpec

import scala.concurrent.Future

class HomePageCachingHelperSpec extends BaseSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder
    .overrides(bind[LocalSessionCache].toInstance(mock[LocalSessionCache]))
    .build()

  override def beforeEach: Unit =
    reset(injected[LocalSessionCache])

  "Calling HomePageCachingHelper.hasUserDismissedUrInvitation" must {
    trait LocalSetup {

      def urBannerDismissedValueInSessionCache: Option[Boolean]

      lazy val cachingHelper: HomePageCachingHelper = {

        val c = injected[HomePageCachingHelper]

        when(injected[LocalSessionCache].fetch()(any(), any())) thenReturn {

          Future.successful {
            urBannerDismissedValueInSessionCache.map { myBool =>
              CacheMap("id", Map("urBannerDismissed" -> JsBoolean(myBool)))
            }
          }
        }
        c
      }

      lazy val hasUserDismissedUrInvitationResult: Boolean = cachingHelper.hasUserDismissedUrInvitation.futureValue
    }

    "return true if cached value returns true" in new LocalSetup {
      lazy val urBannerDismissedValueInSessionCache = Some(true)

      hasUserDismissedUrInvitationResult mustBe true

      verify(cachingHelper.sessionCache, times(1)).fetch()(any(), any())
    }

    "return false if cached value returns false" in new LocalSetup {
      lazy val urBannerDismissedValueInSessionCache = Some(false)

      hasUserDismissedUrInvitationResult mustBe false

      verify(cachingHelper.sessionCache, times(1)).fetch()(any(), any())
    }

    "return false if cache returns no record" in new LocalSetup {
      lazy val urBannerDismissedValueInSessionCache = None

      hasUserDismissedUrInvitationResult mustBe false

      verify(cachingHelper.sessionCache, times(1)).fetch()(any(), any())
    }
  }

  "Calling HomePageCachingHelper.StoreUserUrDismissal" must {

    trait LocalSetup {

      lazy val cachingHelper = {

        val c = injected[HomePageCachingHelper]

        when(injected[LocalSessionCache].cache(any(), any())(any(), any(), any())) thenReturn {
          Future.successful(CacheMap("id", Map.empty))
        }
        c
      }
    }

    "Store true in session cache" in new LocalSetup {

      val r = cachingHelper.storeUserUrDismissal()
      verify(cachingHelper.sessionCache, times(1)).cache(meq("urBannerDismissed"), meq(true))(any(), any(), any())
    }
  }

}
