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

import com.google.inject.ImplementedBy
import org.joda.time.DateTime
import uk.gov.hmrc.time.{CurrentTaxYear, TaxYear}

@ImplementedBy(classOf[TaxYearRetrieverImpl])
trait TaxYearRetriever {
  def currentYear: Int
}

class TaxYearRetrieverImpl extends TaxYearRetriever with CurrentTaxYear {

  override def now: () => DateTime = () => DateTime.now()

  def currentYear: Int = current.currentYear

}
