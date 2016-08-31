package meghanada.session.subscribe;

import meghanada.session.SessionEventBus;

abstract class AbstractSubscriber {

    SessionEventBus sessionEventBus;

    AbstractSubscriber(SessionEventBus sessionEventBus) {
        this.sessionEventBus = sessionEventBus;
    }

}
