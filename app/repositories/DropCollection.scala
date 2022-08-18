package repositories

import scala.concurrent.Future
import javax.inject._
import play.api.inject.ApplicationLifecycle

@Singleton
class DropCollection @Inject() (lifecycle: ApplicationLifecycle, editAddressLockRepository: EditAddressLockRepository) {

  // Start up code here
  editAddressLockRepository.dropCollection()

  // Shut-down hook
  lifecycle.addStopHook { () =>
    Future.successful(())
  }
  //...
}