package views.html.ambiguousjourney

import config.ConfigDecorator
import models.PertaxContext
import models.dto.AmbiguousUserFlowDto
import org.jsoup.Jsoup
import play.api.i18n.Messages
import play.api.test.FakeRequest
import util.{BaseSpec, Fixtures}

class usedUtrToRegisterChoiceSpec extends BaseSpec {

  implicit val messages = Messages.Implicits.applicationMessages

  "usedUtrToRegisterChoice view" should {
    "check page contents" in {
      val pertaxUser = Fixtures.buildFakePertaxUser(isGovernmentGateway = true, isHighGG = true)
      val form  =  AmbiguousUserFlowDto.form
      val document = Jsoup.parse(views.html.ambiguousjourney.usedUtrToRegisterChoice(form)(PertaxContext(FakeRequest("GET", "/test"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(pertaxUser)), messages).toString)
      document.getElementsByTag("h1").text shouldBe Messages("label.have_you_used_your_utr_to_enrol")
      document.getElementsContainingOwnText(Messages("label.your_utr_is_a_ten_digit_number_we_sent_by_post")).hasText shouldBe true
    }
  }

}
