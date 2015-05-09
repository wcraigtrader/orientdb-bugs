#! /usr/bin/env groovy

import org.apache.commons.math3.random.*

class Foo {
    String value
    Bar bar

    Foo( String s ) {
        value = s
    }
    
    def initialize() {
        bar = new Bar( 123456789L )
    }
    
    static void main( String[] args ) {
        Foo foo = new Foo( 'something:else' )
        foo.initialize()
    }
}

@Grab('org.apache.commons:commons-math3:3.5')
class Bar {
    RandomGenerator rand
    
    Bar( long seed ) {
        this( new MersenneTwister( seed ) )
    }
    
    Bar( RandomGenerator r ) {
        rand = r
    }
}

String[] args = []
Foo.main( args )
    