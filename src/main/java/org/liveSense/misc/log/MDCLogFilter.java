package org.liveSense.misc.log;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.slf4j.MDC;

@Component(immediate=true, metatype=true)
@Service
@Properties(value={
		@Property(name="sling.filter.scope", value={"REQUEST"} ),
		@Property(name="felix.webconsole.title", value="liveSense Log viewer"),
		@Property(name="felix.webconsole.configprinter.modes", value="always")})
public class MDCLogFilter implements Filter {

	public void init(FilterConfig filterConfig) throws ServletException {
	}

	
    static String[] split(final String authData) {
        String[] parts = StringUtils.split(authData, "@", 3);
        if (parts != null && parts.length == 3) {
            return parts;
        }
        return null;
    }

    String getUserId(final String authData) {
        if (authData != null) {
            String[] parts = split(authData);
            if (parts != null) {
                return parts[2];
            }
        }
        return "anonymous";
    }
    

    public String extractAuthenticationInfo(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("sling.formauth".equals(cookie.getName())) {
                    // found the cookie, so try to extract the credentials
                    // from it and reverse the base64 encoding
                    String value = cookie.getValue();
                    if (value.length() > 0) {
                        try {
                            return new String(Base64.decodeBase64(value),
                                "UTF-8");
                        } catch (UnsupportedEncodingException e1) {
                            throw new RuntimeException(e1);
                        }
                    }
                }
            }
        }
        return null;
    }

	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		if (request instanceof HttpServletRequest) {
			HttpServletRequest req = (HttpServletRequest)request;
			MDC.put("userName", getUserId(extractAuthenticationInfo(req)));
			MDC.put("type", "HTTP");
		}
	}

	public void destroy() {
	}

}
