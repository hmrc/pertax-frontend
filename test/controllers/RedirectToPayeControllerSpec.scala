/*
 * Copyright 2025 HM Revenue & Customs
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
import controllers.auth.AuthJourney
import org.mockito.Mockito.reset
import play.api.Application
import play.api.inject.bind
import testUtils.BaseSpec

class RedirectToPayeControllerSpec extends BaseSpec {

 private val mockConfigDecorator: ConfigDecorator       = mock[ConfigDecorator]
  override implicit lazy val app: Application = localGuiceApplicationBuilder()
      .overrides(bind[AuthJourney].toInstance(mockAuthJourney))
      .build()

  private lazy val controller = app.injector.instanceOf[RedirectToPayeController]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthJourney)
    reset(mockConfigDecorator)
  }


  "redirectToPaye" must {

    "redirect to PEGA when feature flag enabled and NINO matches redirect list and no trusted helper" in {

    }

    "redirect to TAI when feature flag enabled but NINO does not match redirect list" in {

    }
    
    "redirect to TAI when feature flag enabled and NINO matches but trusted helper is present" in {
      
    }
    
    "redirect to TAI when feature flag is disabled even if NINO matches redirect list" in {
      
    }
  }

}
