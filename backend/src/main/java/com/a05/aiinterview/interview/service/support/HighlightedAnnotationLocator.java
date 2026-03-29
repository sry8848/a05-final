package com.a05.aiinterview.interview.service.support;

import com.a05.aiinterview.ai.dto.QuestionDetailEvaluationOutput;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 将模型返回的原文引用定位到答案中的字符区间。
 */
public final class HighlightedAnnotationLocator {

    private static final int MAX_QUOTE_LENGTH = 48;

    private HighlightedAnnotationLocator() {
    }

    public static QuestionDetailEvaluationOutput resolve(String answerText, QuestionDetailEvaluationOutput output) {
        if (output == null) {
            return null;
        }
        output.setHighlightedAnnotations(locate(answerText, extractCandidates(output)));
        return output;
    }

    public static List<QuestionDetailEvaluationOutput.HighlightedAnnotation> locate(
            String answerText,
            List<QuestionDetailEvaluationOutput.HighlightedAnnotation> candidates) {
        if (answerText == null || answerText.isBlank() || candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<QuestionDetailEvaluationOutput.HighlightedAnnotation> located = new ArrayList<>();
        for (QuestionDetailEvaluationOutput.HighlightedAnnotation candidate : candidates) {
            QuestionDetailEvaluationOutput.HighlightedAnnotation resolved = locateSingle(answerText, candidate);
            if (resolved != null) {
                located.add(resolved);
            }
        }
        located.sort(Comparator
                .comparing(QuestionDetailEvaluationOutput.HighlightedAnnotation::getStart)
                .thenComparing(item -> item.getEnd() - item.getStart()));
        return dropOverlaps(located);
    }

    private static List<QuestionDetailEvaluationOutput.HighlightedAnnotation> extractCandidates(
            QuestionDetailEvaluationOutput output) {
        if (output.getHighlightedAnnotations() != null && !output.getHighlightedAnnotations().isEmpty()) {
            return output.getHighlightedAnnotations();
        }
        if (output.getHighlightedSegments() == null || output.getHighlightedSegments().isEmpty()) {
            return List.of();
        }
        return output.getHighlightedSegments().stream()
                .map(item -> QuestionDetailEvaluationOutput.HighlightedAnnotation.builder()
                        .quote(item.getSegment())
                        .label(item.getLabel())
                        .comment(item.getComment())
                        .build())
                .toList();
    }

    private static QuestionDetailEvaluationOutput.HighlightedAnnotation locateSingle(
            String answerText,
            QuestionDetailEvaluationOutput.HighlightedAnnotation candidate) {
        if (candidate == null) {
            return null;
        }
        String quote = safeTrim(candidate.getQuote());
        if (quote.isEmpty() || quote.length() > MAX_QUOTE_LENGTH) {
            return null;
        }
        List<Range> exactMatches = findExactMatches(answerText, quote);
        if (exactMatches.size() == 1) {
            return buildResolved(candidate, exactMatches.getFirst().start(), exactMatches.getFirst().end());
        }
        if (!exactMatches.isEmpty()) {
            return null;
        }

        NormalizedText normalizedAnswer = normalize(answerText);
        NormalizedText normalizedQuote = normalize(quote);
        if (normalizedQuote.text().isBlank() || normalizedQuote.text().length() > MAX_QUOTE_LENGTH) {
            return null;
        }
        List<Range> normalizedMatches = findNormalizedMatches(normalizedAnswer, normalizedQuote.text());
        if (normalizedMatches.size() != 1) {
            return null;
        }
        Range match = normalizedMatches.getFirst();
        int start = normalizedAnswer.originalIndexes().get(match.start());
        int end = normalizedAnswer.originalIndexes().get(match.end() - 1) + 1;
        return buildResolved(candidate, start, end);
    }

    private static QuestionDetailEvaluationOutput.HighlightedAnnotation buildResolved(
            QuestionDetailEvaluationOutput.HighlightedAnnotation source,
            int start,
            int end) {
        if (start < 0 || end <= start) {
            return null;
        }
        String label = "weakness".equalsIgnoreCase(safeTrim(source.getLabel())) ? "weakness" : "strength";
        return QuestionDetailEvaluationOutput.HighlightedAnnotation.builder()
                .start(start)
                .end(end)
                .quote(safeTrim(source.getQuote()))
                .label(label)
                .comment(safeTrim(source.getComment()))
                .build();
    }

    private static List<QuestionDetailEvaluationOutput.HighlightedAnnotation> dropOverlaps(
            List<QuestionDetailEvaluationOutput.HighlightedAnnotation> annotations) {
        List<QuestionDetailEvaluationOutput.HighlightedAnnotation> result = new ArrayList<>();
        int lastEnd = -1;
        for (QuestionDetailEvaluationOutput.HighlightedAnnotation annotation : annotations) {
            if (annotation.getStart() == null || annotation.getEnd() == null) {
                continue;
            }
            if (annotation.getStart() < lastEnd) {
                continue;
            }
            result.add(annotation);
            lastEnd = annotation.getEnd();
        }
        return result;
    }

    private static List<Range> findExactMatches(String text, String quote) {
        List<Range> matches = new ArrayList<>();
        int from = 0;
        while (from <= text.length() - quote.length()) {
            int index = text.indexOf(quote, from);
            if (index < 0) {
                break;
            }
            matches.add(new Range(index, index + quote.length()));
            from = index + 1;
        }
        return matches;
    }

    private static List<Range> findNormalizedMatches(NormalizedText normalizedText, String normalizedQuote) {
        List<Range> matches = new ArrayList<>();
        int from = 0;
        while (from <= normalizedText.text().length() - normalizedQuote.length()) {
            int index = normalizedText.text().indexOf(normalizedQuote, from);
            if (index < 0) {
                break;
            }
            matches.add(new Range(index, index + normalizedQuote.length()));
            from = index + 1;
        }
        return matches;
    }

    private static NormalizedText normalize(String input) {
        StringBuilder normalized = new StringBuilder();
        List<Integer> originalIndexes = new ArrayList<>();
        boolean previousWhitespace = false;
        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            if (Character.isWhitespace(current)) {
                if (previousWhitespace) {
                    continue;
                }
                normalized.append(' ');
                originalIndexes.add(i);
                previousWhitespace = true;
                continue;
            }
            previousWhitespace = false;
            normalized.append(normalizeChar(current));
            originalIndexes.add(i);
        }
        return new NormalizedText(normalized.toString(), originalIndexes);
    }

    private static char normalizeChar(char current) {
        return switch (current) {
            case '“', '”', '＂' -> '"';
            case '‘', '’' -> '\'';
            case '，' -> ',';
            case '。' -> '.';
            case '：' -> ':';
            case '；' -> ';';
            case '（' -> '(';
            case '）' -> ')';
            default -> current;
        };
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private record Range(int start, int end) {
    }

    private record NormalizedText(String text, List<Integer> originalIndexes) {
    }
}
