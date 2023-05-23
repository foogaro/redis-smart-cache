package com.redis.smartcache.cli.util;

import com.redis.smartcache.cli.structures.RuleType;
import com.redis.smartcache.core.Config;
import com.redis.smartcache.core.RuleConfig;
import io.airlift.units.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Util {
    public static String repeat(char c, int num){
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < num; i++){
            sb.append(c);
        }

        return sb.toString();
    }
    public static String center(String s, int width){
        if (s.length() > width){
            if (width < 3){
                return repeat('.', width);
            }

            return s.substring(0, width-3) + repeat('.',3);
        }

        int spaces = (width - s.length()) / 2;

        StringBuilder sb = new StringBuilder();
        sb.append(repeat(' ', spaces));
        sb.append(s);
        sb.append(repeat(' ', width-(spaces+s.length())));
        return sb.toString();
    }

    public static Optional<Integer> tryParseInt(String s){
        try{
            return Optional.of(Integer.parseInt(s));
        } catch (NumberFormatException e){
            return Optional.empty();
        }
    }

    public static Optional<Long> tryParseLong(String s){
        try{
            return Optional.of(Long.parseLong(s));
        } catch (NumberFormatException e){
            return Optional.empty();
        }
    }

    public static Optional<Double> tryParseDouble(String s){
        try{
            return Optional.of(Double.parseDouble(s));
        } catch (NumberFormatException e){
            return Optional.empty();
        }
    }

    public static List<String> chopString(String s, int width){
        List<String> substrings = new ArrayList<>();

        for(int i = 0; i < s.length(); i+= width){
            int endIndex = Math.min(i+width, s.length());
            substrings.add(s.substring(i, endIndex));
        }

        return substrings;
    }

    public static RuleConfig createRule(RuleType ruleType, String match, Duration ttl){
        RuleConfig.Builder builder = new RuleConfig.Builder().ttl(ttl);
        switch (ruleType){
            case QUERY_IDS:
                builder.queryIds(match);
                break;
            case TABLES_ANY:
                builder.tablesAny(match);
                break;
            case TABLES:
                builder.tables(match);
                break;
            case TABLES_ALL:
                builder.tablesAll(match);
                break;
            case REGEX:
                builder.regex(match);
                break;
        }

        return builder.build();
    }
}
