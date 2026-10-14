package com.larenegocios.alugafacil.infra.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final String CORPO_429 =
            "{\"erro\":\"Muitas requisicoes. Aguarde um instante e tente novamente.\"}";

    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private RateLimitProperties propriedades;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !propriedades.isEnabled() || HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        CategoriaRateLimit categoria = CategoriaRateLimit.de(caminho);
        // Atras de proxy configure server.forward-headers-strategy=native, senao todos os clientes parecem o mesmo IP.
        RateLimiter.Decisao decisao = rateLimiter.tentar(categoria, request.getRemoteAddr());

        if (decisao.permitida()) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("Rate limit excedido: categoria={} ip={} caminho={}",
                categoria, request.getRemoteAddr(), request.getRequestURI());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decisao.segundosParaTentarNovamente()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(CORPO_429);
    }
}
