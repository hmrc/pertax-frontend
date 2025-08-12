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

package services

import config.ConfigDecorator
import org.mockito.Mockito.{reset, when}
import play.api.{Environment, Mode}
import testUtils.BaseSpec

import java.io.File

class URLServiceSpec extends BaseSpec {

  private val testEnv: Environment  = Environment(new File(""), classOf[URLServiceSpec].getClassLoader, Mode.Test)
  private val devEnv: Environment   = Environment(new File(""), classOf[URLServiceSpec].getClassLoader, Mode.Dev)
  private val mockApplicationConfig = mock[ConfigDecorator]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockApplicationConfig)
  }

  "localFriendlyUrl" should {
    "return the original url if it is in the test environment" in {
      val service = new URLService(mockApplicationConfig, testEnv)
      service.localFriendlyEncodedUrl("/A?a=b&c=d", "B") mustBe "%2FA%3Fa%3Db%26c%3Dd"
    }

    "return url string with localhost and port if is in development (local/ jenkins) environment" in {
      val service = new URLService(mockApplicationConfig, devEnv)
      when(mockApplicationConfig.runMode).thenReturn(Some("Dev"))
      service.localFriendlyEncodedUrl("/A?a=b&c=d", "localhost") mustBe "http%3A%2F%2Flocalhost%2FA%3Fa%3Db%26c%3Dd"
    }

    "return the original url if is in production environment" in {
      val service = new URLService(mockApplicationConfig, devEnv)
      when(mockApplicationConfig.runMode).thenReturn(Some("Prod"))
      service.localFriendlyEncodedUrl("/A?a=b&c=d", "B") mustBe "%2FA%3Fa%3Db%26c%3Dd"
    }

    "if url is absolute then return the original url regardless of environment" in {
      val service = new URLService(mockApplicationConfig, devEnv)
      when(mockApplicationConfig.runMode).thenReturn(Some("Prod"))
      service.localFriendlyEncodedUrl("http://localhost?&=", "B") mustBe "http%3A%2F%2Flocalhost%3F%26%3D"
    }

    "if url is not an accepted url regardless of environment" in {
      val service = new URLService(mockApplicationConfig, devEnv)
      when(mockApplicationConfig.runMode).thenReturn(Some("Prod"))
      service.localFriendlyEncodedUrl("http://A?a=b&c=d", "B") mustBe "B%2Fpersonal-account"
    }

  }
}
