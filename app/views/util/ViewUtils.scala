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

package views.util

import config.ConfigDecorator
import play.api.i18n.Messages
import uk.gov.hmrc.play.language.LanguageUtils
import viewmodels._

import javax.inject.Inject

class ViewUtils @Inject()(languageUtils: LanguageUtils) {

  def fromMessage(message: Message)(implicit messages: Messages): String =
    message match {
      case Text(key, args)     => messages(key, args.map(fromMessage): _*)
      case Date(date, default) => languageUtils.Dates.formatDate(date, default)
      case Literal(value)      => value
    }

  def fromUrl(url: Url)(implicit configDecorator: ConfigDecorator): String =
    url match {
      case MakePaymentUrl            => configDecorator.makePaymentUrl
      case TaxPaidUrl                => configDecorator.taxPaidUrl
      case UnderpaidUrl(year)        => configDecorator.underpaidUrl(year)
      case UnderpaidReasonsUrl(year) => configDecorator.underpaidUrlReasons(year)
      case OverpaidUrl(year)         => configDecorator.overpaidUrl(year)
      case OverpaidReasonsUrl(year)  => configDecorator.overpaidUrlReasons(year)
      case RightAmountUrl(year)      => configDecorator.rightAmountUrl(year)
      case NotCalculatedUrl(year)    => configDecorator.notCalculatedUrl(year)
      case NotEmployedUrl(year)      => configDecorator.notEmployedUrl(year)
      case Empty                     => ""
    }
}
