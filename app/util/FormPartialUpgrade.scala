/*
 * Copyright 2023 HM Revenue & Customs
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

import org.jsoup.Jsoup
import play.twirl.api.Html

object FormPartialUpgrade {

  // TODO: To be deleted. See DDCNL-6008
  def upgrade(partial: Html): Html = {
    val doc = Jsoup.parse(partial.toString)
    doc.getElementsByTag("a").addClass("govuk-link")
    doc.getElementsByTag("ul").removeClass("list-bullet").addClass("govuk-list govuk-list--bullet")
    doc.getElementsByTag("h2").addClass("govuk-heading-m")
    doc.getElementsByTag("p").addClass("govuk-body")
    doc.getElementsByClass("utr-heading").removeClass("utr-heading").addClass("govuk-inset-text")
    Html(doc.select("body").html)
  }
}
