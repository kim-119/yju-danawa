package yju.danawa.com.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * CP949 / EUC-KR 인코딩 호환 필터.
 * 바코드 스캐너나 구형 윈도우 환경에서 CP949/EUC-KR로 전송된 HTTP 파라미터를
 * UTF-8로 안전하게 변환한 뒤 다음 필터/컨트롤러로 전달한다.
 * 변환 실패 시 원본 값을 그대로 사용(UTF-8 fallback).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class EncodingFilter extends OncePerRequestFilter {

    private static final Set<String> LEGACY_ENCODINGS =
            Set.of("CP949", "EUC-KR", "EUC_KR", "MS949", "X-WINDOWS-949");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Charset legacyCharset = detectLegacyCharset(request);
        if (legacyCharset != null) {
            chain.doFilter(new ReencodingRequestWrapper(request, legacyCharset), response);
        } else {
            chain.doFilter(request, response);
        }
    }

    /**
     * 요청의 Character-Encoding 또는 Content-Type 헤더에서 레거시 인코딩을 감지한다.
     * 감지되면 해당 Charset을 반환하고, 감지되지 않으면 null을 반환한다.
     */
    private Charset detectLegacyCharset(HttpServletRequest request) {
        String encoding = request.getCharacterEncoding();
        if (encoding != null) {
            String upper = encoding.toUpperCase(Locale.ROOT);
            for (String legacy : LEGACY_ENCODINGS) {
                if (upper.contains(legacy)) {
                    try {
                        return Charset.forName(encoding);
                    } catch (Exception e) {
                        return Charset.forName("CP949");
                    }
                }
            }
        }

        String contentType = request.getContentType();
        if (contentType != null) {
            String upper = contentType.toUpperCase(Locale.ROOT);
            for (String legacy : LEGACY_ENCODINGS) {
                if (upper.contains(legacy)) {
                    return Charset.forName("CP949");
                }
            }
        }

        return null;
    }

    /**
     * 파라미터를 sourceCharset → UTF-8로 재변환하는 RequestWrapper.
     * Tomcat이 ISO-8859-1로 바이트를 보존한 값을 올바른 인코딩으로 복원한다.
     */
    private static class ReencodingRequestWrapper extends HttpServletRequestWrapper {

        private final Map<String, String[]> reencodedParams;

        ReencodingRequestWrapper(HttpServletRequest request, Charset sourceCharset) {
            super(request);
            this.reencodedParams = new HashMap<>();
            for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
                String[] original = entry.getValue();
                String[] converted = new String[original.length];
                for (int i = 0; i < original.length; i++) {
                    converted[i] = transcode(original[i], sourceCharset);
                }
                reencodedParams.put(entry.getKey(), converted);
            }
        }

        /**
         * ISO-8859-1로 저장된 원시 바이트를 sourceCharset으로 재해석하여 UTF-8 String으로 반환.
         * 변환 실패 시 원본 값을 그대로 반환(UTF-8 fallback).
         */
        private String transcode(String value, Charset from) {
            try {
                byte[] raw = value.getBytes(StandardCharsets.ISO_8859_1);
                return new String(raw, from);
            } catch (Exception e) {
                return value;
            }
        }

        @Override
        public String getParameter(String name) {
            String[] vals = reencodedParams.get(name);
            return (vals != null && vals.length > 0) ? vals[0] : null;
        }

        @Override
        public String[] getParameterValues(String name) {
            return reencodedParams.get(name);
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return Collections.unmodifiableMap(reencodedParams);
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.enumeration(reencodedParams.keySet());
        }
    }
}
