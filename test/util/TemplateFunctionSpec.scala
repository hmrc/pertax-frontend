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

package util

import org.scalatest.{Matchers, WordSpec}

class TemplateFunctionSpec extends WordSpec with Matchers {
  "TemplateFunctions.upperCaseToTitleCase" should {
    "convert a name string which is all uppercase" in {
      TemplateFunctions.upperCaseToTitleCase("FIRSTNAME LASTNAME") should be("Firstname Lastname")
    }

    "should not convert a name string in which the first name is uppercase and surname is lowercase" in {
      TemplateFunctions.upperCaseToTitleCase("FIRSTNAME lastname") should be("FIRSTNAME lastname")
    }

    "should not convert a name in which the first name is lowercase and the surname is uppercase" in {
      TemplateFunctions.upperCaseToTitleCase("firstname LASTNAME") should be("firstname LASTNAME")
    }

    "should not convert a name string which the name contains a combination of lower and uppercase" in {
      TemplateFunctions.upperCaseToTitleCase("FiRsTnAmE lAsTnAmE") should be("FiRsTnAmE lAsTnAmE")
    }
  }

  "TemplateFunctions.formatCurrency" should {
    "convert a big decimal into currency format" in {
      TemplateFunctions.formatCurrency(1000.0) should be("1,000.00")
    }
  }
}
