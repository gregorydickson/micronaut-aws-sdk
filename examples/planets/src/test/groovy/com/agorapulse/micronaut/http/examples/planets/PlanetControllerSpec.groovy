package com.agorapulse.micronaut.http.examples.planets

import com.agorapulse.dru.Dru
import com.agorapulse.dru.dynamodb.persistence.DynamoDB
import com.agorapulse.gru.Gru
import com.agorapulse.gru.agp.ApiGatewayProxy
import com.agorapulse.micronaut.agp.ApiGatewayProxyHandler
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.reactivex.Flowable
import org.junit.Rule
import org.reactivestreams.Publisher
import spock.lang.Specification

class PlanetControllerSpec extends Specification {

    @Rule Gru gru = Gru.equip(ApiGatewayProxy.steal(this) {
        map '/planet/{star}' to ApiGatewayProxyHandler
        map '/planet/{star}/{name}' to ApiGatewayProxyHandler
    })

    @Rule Dru dru = Dru.steal(this)

    ApiGatewayProxyHandler handler = new ApiGatewayProxyHandler(){
        @Override
        protected void doWithApplicationContext(ApplicationContext applicationContext) {
            applicationContext.registerSingleton(PlanetDBService, new PlanetDBService(mapper: DynamoDB.createMapper(dru)))
        }
    }

    void setup() {
        dru.add(new Planet(star: 'sun', name: 'mercury'))
        dru.add(new Planet(star: 'sun', name: 'venus'))
        dru.add(new Planet(star: 'sun', name: 'earth'))
        dru.add(new Planet(star: 'sun', name: 'mars'))
    }

    void 'get planet'() {
        expect:
            gru.test {
                get('/planet/sun/earth')
                expect {
                    json 'earth.json'
                }
            }
    }

    void 'get planet which does not exist'() {
        expect:
            gru.test {
                get('/planet/sun/vulcan')
                expect {
                    status NOT_FOUND
                }
            }
    }

    void 'list planets by existing star'() {
        expect:
            gru.test {
                get('/planet/sun')
                expect {
                    json 'planetsOfSun.json'
                }
            }
    }

    void 'add planet'() {
        when:
            gru.test {
                post '/planet/sun/jupiter'
                expect {
                    status CREATED
                    json 'jupiter.json'
                }
            }
        then:
            gru.verify()
            dru.findAllByType(Planet).size() == 5
    }

    void 'delete planet'() {
        given:
            dru.add(new Planet(star: 'sun', name: 'pluto'))
        expect:
            dru.findAllByType(Planet).size() == 5
            gru.test {
                delete '/planet/sun/pluto'
                expect {
                    status NO_CONTENT
                    json 'pluto.json'
                    headers 'x-planet-filter': 'true'
                }
            }
            dru.findAllByType(Planet).size() == 4
    }

}

@Filter('/planet/**') class PlanetFilter implements HttpServerFilter {

    @Override
    Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        Flowable.fromPublisher(chain.proceed(request)).map {
            it.header('x-planet-filter', 'true')
        }
    }
}
