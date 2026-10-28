package com.larenegocios.alugafacil.service;

import com.larenegocios.alugafacil.domain.usuario.Perfil;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import com.larenegocios.alugafacil.domain.usuario.UsuarioRepository;
import com.larenegocios.alugafacil.dto.DadosCadastroUsuario;
import com.larenegocios.alugafacil.infra.exception.RegraDeNegocioException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AutenticacaoService implements UserDetailsService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public static String normalizarEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return usuarioRepository.findByEmail(normalizarEmail(email))
                .orElseThrow(() -> new UsernameNotFoundException("Usuario nao encontrado"));
    }

    public Usuario cadastrar(DadosCadastroUsuario dados) {
        if (dados.perfil() != null && dados.perfil() != Perfil.LOCATARIO) {
            throw new RegraDeNegocioException("O cadastro pelo site e apenas para locatarios");
        }
        return criar(dados, Perfil.LOCATARIO);
    }

    public Usuario cadastrarLocador(DadosCadastroUsuario dados) {
        return criar(dados, Perfil.LOCADOR);
    }

    public Usuario criar(DadosCadastroUsuario dados, Perfil perfil) {
        String email = normalizarEmail(dados.email());
        String cpf = dados.cpf().replaceAll("\\D", "");
        if (cpf.length() != 11) {
            throw new RegraDeNegocioException("CPF invalido: informe os 11 digitos");
        }

        if (usuarioRepository.existsByEmailOrCpf(email, cpf)) {
            throw new RegraDeNegocioException("Ja existe um usuario cadastrado com este email ou CPF");
        }

        Usuario usuario = new Usuario();
        usuario.setNome(dados.nome().trim());
        usuario.setEmail(email);
        usuario.setCpf(cpf);
        usuario.setTelefone(dados.telefone() == null || dados.telefone().isBlank() ? null : dados.telefone().trim());
        usuario.setSenha(passwordEncoder.encode(dados.senha()));
        usuario.setPerfil(perfil);
        usuario.setAtivo(true);
        usuario.setEmailConfirmado(false);

        return usuarioRepository.save(usuario);
    }
}
