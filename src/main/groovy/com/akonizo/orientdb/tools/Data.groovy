package com.akonizo.orientdb.tools

import groovy.util.logging.Slf4j
import org.apache.commons.math3.random.MersenneTwister
import org.apache.commons.math3.random.RandomGenerator

@Slf4j
class Data {

    static final String WORDLIST = "/words.txt"

    /** Word list for node keys and data */
    static List<String> WORDS = null

    /** Random number generator */
    RandomGenerator rand

    /** Seeded constructor */
    Data( long seed ) {
        this( new MersenneTwister( seed ) )
    }

    /** Random constructor */
    Data( RandomGenerator r ) {
        log.debug( "Constructed with ${r.class.simpleName}" )
        this.rand = r

        if (WORDS == null) {
            loadWords()
        }
    }

    def loadWords() {
        log.debug( "Loading dictionary" )
        WORDS = new ArrayList<String>( 100000 )
        this.getClass().getResourceAsStream( WORDLIST ).eachLine { WORDS.add( it ) }
        log.info( "Loaded ${WORDS.size} words" )
    }

    int getRandomAge() {
        return randomSize(50,20)
    }

    String getRandomKey() {
        return random( WORDS ) + '-' + random( WORDS )
    }

    String getRandomName() {
        return random( WORDS ).capitalize() + ' ' + random( WORDS ).capitalize()
    }

    int randomSize( int center, int spread ) {
        return Math.max(1, center + spread * rand.nextGaussian() )
    }

    String randomData( int size=8 ) {
        return ( [random( WORDS)]* size ).join( ' ' )
    }

    String random( List<String> words ) {
        return words[ rand.nextInt( words.size() ) ]
    }

    /** Simple Fibonacci numbers */
    static int fib( int f ) {
        assert f <= 46
        if (f <= 1) return f
        return fib( f-1 ) + fib( f-2 )
    }
}
