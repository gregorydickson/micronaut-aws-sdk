package com.agorapulse.micronaut.aws.kinesis

// FIXME - is this still required?
abstract class AbstractEventService {

    def handleEvent(AbstractEvent event) {
        // To override
    }

    boolean validateEvent(AbstractEvent event) {
        // Filter event if consumerFilterKey is defined in config (to filter events between devs using the same streams)
        (!event.consumerFilterKey && !kinesisConfig?.consumerFilterKey) || (kinesisConfig?.consumerFilterKey && event.consumerFilterKey == kinesisConfig?.consumerFilterKey)
    }

    // PROTECTED

    protected def getKinesisConfig() {
        grailsApplication.config.grails?.plugins?.awssdk?.kinesis ?: grailsApplication.config.grails?.plugin?.awssdk?.kinesis
    }

}
