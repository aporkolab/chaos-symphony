package hu.porkolab.chaosSymphony.dlq.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.*;

class SecurityFilterTest {

    @Test
    void rejectsWhenNoToken() throws Exception {
        var f = new SecurityFilter();

        var req = mock(HttpServletRequest.class);
        var res = mock(HttpServletResponse.class);
        var chain = mock(FilterChain.class);

        when(req.getHeader("X-Admin-Token")).thenReturn(null);

        f.doFilter(req, res, chain);

        verify(res).sendError(401, "Unauthorized");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void acceptsWhenTokenMatches() throws Exception {
        System.setProperty("DLQ_ADMIN_TOKEN", "dev-token");

        var f = new SecurityFilter();
        var req = mock(HttpServletRequest.class);
        var res = mock(HttpServletResponse.class);
        var chain = mock(FilterChain.class);

        when(req.getHeader("X-Admin-Token")).thenReturn("dev-token");

        f.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
    }
}
