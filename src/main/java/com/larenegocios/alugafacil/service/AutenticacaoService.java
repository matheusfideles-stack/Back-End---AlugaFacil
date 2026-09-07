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

@Service
public class AutenticacaoService implements UserDetailsService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public UserDetails loadUserByUsername(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario nao encontrado"));
    }

    public Usuario cadastrar(DadosCadastroUsuario dados) {
        if (usuarioRepository.existsByEmailOrCpf(dados.email(), dados.cpf())) {
            throw new RegraDeNegocioException("Ja existe um usuario cadastrado com este email ou CPF");
        }

        Usuario usuario = new Usuario();
        usuario.setNome(dados.nome());
        usuario.setEmail(dados.email());
        usuario.setCpf(dados.cpf());
        usuario.setTelefone(dados.telefone());
        usuario.setSenha(passwordEncoder.encode(dados.senha()));
        usuario.setPerfil(dados.perfil() != null ? dados.perfil() : Perfil.LOCATARIO);
        usuario.setAtivo(true);

        return usuarioRepository.save(usuario);
    }
}
