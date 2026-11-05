package io.github.suzanacavalcante.desafio.cliente;

import io.github.suzanacavalcante.desafio.contato.Contato;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Raiz do agregado: um cliente possui zero ou mais contatos, que só existem enquanto o cliente existe.
 * Os contatos são alterados somente por {@link #adicionarContato(Contato)} e
 * {@link #removerContato(Contato)}, que mantêm os dois lados do relacionamento sincronizados.
 */
@Entity
@Table(name = "clientes")
@Getter
@Setter
@NoArgsConstructor
public class Cliente {

    public static final int NOME_MAX = 150;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = NOME_MAX)
    private String nome;

    @Setter(AccessLevel.NONE)
    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Contato> contatos = new ArrayList<>();

    /** Visão somente leitura; para alterar use {@link #adicionarContato} e {@link #removerContato}. */
    public List<Contato> getContatos() {
        return Collections.unmodifiableList(contatos);
    }

    public void adicionarContato(Contato contato) {
        contatos.add(contato);
        contato.setCliente(this);
    }

    public void removerContato(Contato contato) {
        if (contatos.remove(contato)) {
            contato.setCliente(null);
        }
    }
}
