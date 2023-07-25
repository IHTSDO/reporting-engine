/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.b2international.commons;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Locale;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Doubles;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AcceptHeader<T> implements Comparable<AcceptHeader<T>> {

	private static Logger LOGGER = LoggerFactory.getLogger(AcceptHeader.class);

    private final T value;
    private final double quality;

    protected AcceptHeader(T value, double quality) {
        this.value = value;
        this.quality = quality;
    }

    public T getValue() {
        return value;
    }

    public double getQuality() {
        return quality;
    }
    
	@Override
	public int compareTo(AcceptHeader<T> other) {
		return Doubles.compare(quality, other.quality);
	}

    private static <T> List<T> parse(StringReader input, Function<String, T> converterFunction) throws IOException {
        final ImmutableList.Builder<AcceptHeader<T>> resultBuilder = ImmutableList.builder();

        do {
            String token = HttpParser.readToken(input);
            if (token == null) {
                HttpParser.skipUntil(input, 0, ',');
                continue;
            }

            if (token.length() == 0) {
                // No more data to read
                break;
            }

            // See if a quality has been provided
            double quality = 1;
            SkipResult lookForSemiColon = HttpParser.skipConstant(input, ";");
            if (lookForSemiColon == SkipResult.FOUND) {
                quality = HttpParser.readWeight(input, ',');
            }

            if (quality > 0) {
                resultBuilder.add(new AcceptHeader<T>(converterFunction.apply(token), quality));
            }
        } while (true);

        // Stable sort ensures that values with the same quality are not reordered
        final List<AcceptHeader<T>> sortedResults = Ordering.natural().reverse().sortedCopy(resultBuilder.build());
        
        return FluentIterable.from(sortedResults).transform(new Function<AcceptHeader<T>, T>() {
        	@Override
        	public T apply(AcceptHeader<T> input) {
        		return input.getValue();
        	}
		}).toList();
    }
    
    public static List<Locale> parseLocales(StringReader input) throws IOException {
    	return parse(input, new Function<String, Locale>() {
    		@Override
    		public Locale apply(String input) {
    			return Locale.forLanguageTag(input);
    		}
		});
    }
    
    public static List<Long> parseLongs(StringReader input) throws IOException {
    	return parse(input, new Function<String, Long>() {
    		@Override
    		public Long apply(String input) {
    			return Long.valueOf(input);
    		}
		});
    }
    
    public static List<ExtendedLocale> parseExtendedLocales(StringReader input) throws IOException {
    	return parse(input, new Function<String, ExtendedLocale>() {
    		@Override
    		public ExtendedLocale apply(String input) {
    			return ExtendedLocale.valueOf(input);
    		}
		});
    }
}
