package io.github.suzanacavalcante.desafio.contato;

import io.github.suzanacavalcante.desafio.cliente.Cliente;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "contatos")
@Getter
@NoArgsConstructor
public class Contato {

    public static final int TELEFONE_MAX = 30;
    public static final int EMAIL_MAX = 254;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false, length = TELEFONE_MAX)
    private String telefone;

    @Setter
    @Column(nullable = false, length = EMAIL_MAX)
    private String email;

    /**
     * O setter é público porque {@code Cliente} fica em outro pacote (Contato tem repository, service e
     * controller próprios). Não chame diretamente: use {@code Cliente.adicionarContato(...)}, que mantém
     * os dois lados do relacionamento sincronizados.
     */
    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;
}
