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
import java.util.Objects;

/**
 * Raiz do agregado: um cliente possui zero ou mais contatos, que só existem enquanto o cliente existe.
 * Os contatos são alterados somente por {@link #adicionarContato(Contato)} e
 * {@link #removerContato(Contato)}, que mantêm os dois lados do relacionamento sincronizados.
 * <p>
 * Um contato pertence a um único cliente durante toda a sua vida: não há "mover" contato entre clientes.
 * Remover um contato o <b>apaga</b> (via {@code orphanRemoval}); não o reutilize em outro cliente, pois
 * reanexar no mesmo flush um objeto removido por cascata faz o Hibernate falhar.
 */
@Entity
@Table(name = "clientes")
@Getter
@NoArgsConstructor
public class Cliente {

    public static final int NOME_MAX = 150;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false, length = NOME_MAX)
    private String nome;

    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Contato> contatos = new ArrayList<>();

    /** Visão somente leitura; para alterar use {@link #adicionarContato} e {@link #removerContato}. */
    public List<Contato> getContatos() {
        return Collections.unmodifiableList(contatos);
    }

    /**
     * Associa o contato a este cliente. É idempotente: adicionar o mesmo contato de novo não o duplica.
     *
     * @throws NullPointerException  se {@code contato} for {@code null}
     * @throws IllegalStateException se o contato já pertencer a outro cliente (nada é alterado)
     */
    public void adicionarContato(Contato contato) {
        Objects.requireNonNull(contato, "contato");
        Cliente atual = contato.getCliente();
        if (atual != null && atual != this) {
            throw new IllegalStateException("O contato já pertence a outro cliente");
        }
        if (!contatos.contains(contato)) {
            contatos.add(contato);
        }
        contato.setCliente(this);
    }

    /**
     * Remove o contato deste cliente (o {@code orphanRemoval} o apaga no flush; não o reutilize).
     * <p>
     * Atenção: a busca usa {@code equals}, que aqui é a identidade do objeto (as entidades não
     * sobrescrevem {@code equals}/{@code hashCode}, pois o id é {@code null} antes do persist).
     * Passe sempre uma instância obtida de {@link #getContatos()} deste mesmo cliente, na mesma sessão;
     * um contato carregado em outra sessão ou um proxy não será encontrado e nada acontece.
     * Para remover por id, localize a instância em {@code getContatos()} e remova-a.
     *
     * @return {@code true} se o contato foi removido
     */
    public boolean removerContato(Contato contato) {
        if (contatos.remove(contato)) {
            contato.setCliente(null);
            return true;
        }
        return false;
    }
}
