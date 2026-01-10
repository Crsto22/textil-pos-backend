package com.sistemapos.sistematextil.services;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.config.JwtService;
import com.sistemapos.sistematextil.model.CustomUser;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.util.AuthenticationRequest;
import com.sistemapos.sistematextil.util.AuthenticationResponse;
import com.sistemapos.sistematextil.util.RegisterRequest;
import com.sistemapos.sistematextil.util.Rol;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UsuarioRepository usuarioRepository;
    private final SucursalRepository sucursalRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

   

    // El RegisterRequest viene del paquete util
    public String register (RegisterRequest request){
        Sucursal sucursal = sucursalRepository.findById(request.idSucursal()).orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));
        var user = Usuario.builder()
            .nombre(request.nombre()) //Los nombres como .nombre() , .apellido() etc , tienen que ser los mismos de mi atributo de la entidad
            .apellido(request.apellido())
            .dni(request.dni())
            .correo(request.email())
            .telefono(request.telefono())
            .password(passwordEncoder.encode(request.password()))
            .rol(Rol.ADMINISTRADOR)
            .sucursal(sucursal) // <- aquí va el objeto Sucursal
            .build();
        usuarioRepository.save(user);

        return "Usuario registrado exitosamente";

    }



    // EL AuthenticationResponse VIENE DE util, AuthenticationResponse

    // El AuthenticationRequest viene del paquete util (Al momento de iniciar sesion valida credenciales y se genera un token)
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password())); //request.email(), request.password() , correo y contraseña que el usuario ingreso lo envia al AuthenticationManager de SecurityConfig (VALIDA QUE EL USUARIO EXISTA EN LA DATABASEE) y si valida y existe recien pasa a la siguiente linea (ES DECIR ESTA LINEA HACE EL PROCESO DE LOGIN)
        //↑↑ VALIDA QUE EXISTA EN LA DATABASE
        var user = usuarioRepository.findByCorreo(request.email()).orElseThrow(); //Obtiene el correo que el usuario ingreso al iniciar sesion
        
        //ENVOLVEMOS el usuario en un CustomUser (Que implementa un UserDetails)
        CustomUser customUser = new CustomUser(user);

        //Ponemos el customUser en los jwt , porque en nuestro jwtService esta codificado para que los token se guarden en el custonUser que envuelve la entidad Usuario, ademas para el spring security se maneja  solo con customUser
        var jwtToken = jwtService.generateToken(customUser);
        var refreshToken = jwtService.generateRefreshToken(customUser);

        //Me retornara o devolvera esto y nuestro controlador devolvera esto al frontend
        return new AuthenticationResponse(
            jwtToken,
            refreshToken,
            user.getIdUsuario(),
            user.getNombre(),
            user.getApellido(),
            user.getRol().name(),
            user.getSucursal().getIdSucursal() //obtener id de sucursal
        );


    }

}
