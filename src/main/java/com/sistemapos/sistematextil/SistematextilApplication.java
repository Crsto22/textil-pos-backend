package com.sistemapos.sistematextil;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.sistemapos.sistematextil.config.BrevoProperties;
import com.sistemapos.sistematextil.config.StorageProperties;
import com.sistemapos.sistematextil.config.SunatProperties;
import com.sistemapos.sistematextil.config.TurnstileProperties;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.util.usuario.Rol;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.sistemapos.sistematextil.repositories")
@EntityScan(basePackages = "com.sistemapos.sistematextil.model")
@EnableScheduling
@EnableConfigurationProperties({ SunatProperties.class, StorageProperties.class, BrevoProperties.class, TurnstileProperties.class })
public class SistematextilApplication {

	public static void main(String[] args) {
		SpringApplication.run(SistematextilApplication.class, args);
	}

	
	@Bean
	CommandLineRunner commandLineRunner(UsuarioRepository usuarioRepository, SucursalRepository sucursalRepository,
			PasswordEncoder encoder) {
		return args -> {
			System.out.println("Creando usuario de prueba...");

			// Revisamos si ya existe el usuario
			if (usuarioRepository.findByCorreo("abel@gmail.com").isEmpty()) {

				// Obtenemos una sucursal  (ejemplo la sucursal con id 1)
				 Sucursal sucursal = sucursalRepository.findById(1).orElseThrow(() -> new RuntimeException("Sucursal con ID 1 no encontrada"));
				// Creamos el usuario
				Usuario usuario = new Usuario();
				usuario.setNombre("Abel");
				usuario.setApellido("Durand");
				usuario.setDni("12345678"); // Debe tener 8 dígitos
				usuario.setTelefono("922382875"); // 9 dígitos
				usuario.setCorreo("abel@gmail.com");
				usuario.setPassword(encoder.encode("12345678")); // al menos 8 caracteres
				usuario.setRol(Rol.ADMINISTRADOR);
				usuario.setSucursal(sucursal); // asignamos la sucursal
				usuario.setEstado("ACTIVO"); // aunque el @PrePersist también lo hace
				// Guardamos en la base de datos
				usuarioRepository.save(usuario);

				System.out.println("Usuario de prueba creado con éxito");
			} else {
				System.out.println("Usuario de prueba ya existe");
			}

			if (usuarioRepository.findByCorreo("sistema@gmail.com").isEmpty()) {
				Usuario usuarioSistema = new Usuario();
				usuarioSistema.setNombre("Sistema");
				usuarioSistema.setApellido("Tecnico");
				usuarioSistema.setDni("87654321");
				usuarioSistema.setTelefono("900000001");
				usuarioSistema.setCorreo("sistema@gmail.com");
				usuarioSistema.setPassword(encoder.encode("12345678"));
				usuarioSistema.setRol(Rol.SISTEMA);
				usuarioSistema.setSucursal(null);
				usuarioSistema.setTurno(null);
				usuarioSistema.setEstado("ACTIVO");
				usuarioRepository.save(usuarioSistema);

				System.out.println("Usuario de sistema creado con exito");
			} else {
				System.out.println("Usuario de sistema ya existe");
			}
		};
	}
	
}
