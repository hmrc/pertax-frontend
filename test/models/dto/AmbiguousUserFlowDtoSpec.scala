package models.dto

import util.BaseSpec

class AmbiguousUserFlowDtoSpec extends BaseSpec {

  "AmbiguousUserFlowDto" should {
    "fail on invalid data" in {
      AmbiguousUserFlowDto.form.bind(Map.empty[String, String]).fold(
        formErrors => {
          formErrors.errors.length shouldBe 1
          formErrors.errors.head.message shouldBe "error.enrolled.to.send.tax.required"

        },
        _ => fail("There is a problem")

      )

    }
  }

}
