package com.larenegocios.alugafacil.service;

import com.larenegocios.alugafacil.domain.imovel.Imovel;
import com.larenegocios.alugafacil.domain.usuario.Perfil;
import com.larenegocios.alugafacil.domain.usuario.Usuario;

import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

// Apoio dos testes unitarios dos services. O Mockito nao funciona no JDK desta maquina, entao os
// repositorios sao falsos criados na mao: so respondem aos metodos que o teste declara.
final class Dobles {

    private Dobles() {
    }

    static final class Repositorio<T> {

        private final Class<T> tipo;
        private final Map<String, Function<Object[], Object>> respostas = new HashMap<>();

        private Repositorio(Class<T> tipo) {
            this.tipo = tipo;
        }

        Repositorio<T> responde(String metodo, Function<Object[], Object> resposta) {
            respostas.put(metodo, resposta);
            return this;
        }

        Repositorio<T> sempre(String metodo, Object valor) {
            return responde(metodo, args -> valor);
        }

        T criar() {
            Object proxy = Proxy.newProxyInstance(tipo.getClassLoader(), new Class<?>[]{tipo}, (p, metodo, args) -> {
                Object[] argumentos = args == null ? new Object[0] : args;
                Function<Object[], Object> resposta = respostas.get(metodo.getName());
                if (resposta != null) {
                    return resposta.apply(argumentos);
                }
                return switch (metodo.getName()) {
                    case "toString" -> "Dobles(" + tipo.getSimpleName() + ")";
                    case "hashCode" -> System.identityHashCode(p);
                    case "equals" -> p == argumentos[0];
                    default -> throw new UnsupportedOperationException(
                            tipo.getSimpleName() + "." + metodo.getName() + " nao foi preparado neste teste");
                };
            });
            return tipo.cast(proxy);
        }
    }

    static <T> Repositorio<T> repositorio(Class<T> tipo) {
        return new Repositorio<>(tipo);
    }

    // Notificador de verdade ligado a um e-mail de mentira: os testes de unidade conferem os envios por la.
    static NotificacaoDeEventos notificador(ConfirmacaoEmailServiceTest.EmailFalso email) {
        NotificacaoDeEventos notificador = new NotificacaoDeEventos();
        ReflectionTestUtils.setField(notificador, "envioDeEmail", email);
        ReflectionTestUtils.setField(notificador, "urlBase", "http://localhost:8080");
        return notificador;
    }

    static NotificacaoDeEventos notificador() {
        return notificador(new ConfirmacaoEmailServiceTest.EmailFalso());
    }

    static Usuario usuario(long id, Perfil perfil) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNome("Usuario " + id);
        usuario.setEmail("usuario" + id + "@teste.com");
        usuario.setPerfil(perfil);
        return usuario;
    }

    static Imovel imovel(long id, Usuario locador) {
        Imovel imovel = new Imovel();
        imovel.setId(id);
        imovel.setLocador(locador);
        imovel.setTitulo("Imovel " + id);
        imovel.setValorAluguel(new BigDecimal("1500.00"));
        imovel.setValorCondominio(new BigDecimal("300.00"));
        imovel.setDisponivel(true);
        return imovel;
    }
}
