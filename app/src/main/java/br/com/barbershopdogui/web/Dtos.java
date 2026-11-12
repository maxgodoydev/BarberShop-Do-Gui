package br.com.barbershopdogui.web;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** Objetos de entrada e saída da API. */
public final class Dtos {
    private Dtos() { }

    // ---------- catálogo ----------
    public record ServiceView(Long id, String nome, String descricao, BigDecimal preco, int duracaoMinutos,
                              boolean requerAvaliacao, boolean ilustrativo, boolean ativo) { }

    public record ServiceInput(@NotBlank(message = "Informe o nome.") @Size(max = 100) String nome,
                               @NotBlank(message = "Informe a descrição.") @Size(max = 240) String descricao,
                               @NotNull(message = "Informe o preço.") @Min(value = 0, message = "O preço não pode ser negativo.") BigDecimal preco,
                               @NotNull(message = "Informe a duração.") @Min(value = 5, message = "Duração mínima: 5 minutos.")
                               @Max(value = 480, message = "Duração máxima: 480 minutos.") Integer duracaoMinutos,
                               boolean requerAvaliacao, boolean ilustrativo, Integer ordem) { }

    public record ProfessionalView(Long id, String nome, boolean ativo, List<Long> servicoIds) { }

    public record ProfessionalInput(@NotBlank(message = "Informe o nome.") @Size(max = 100) String nome, List<Long> servicoIds, Integer ordem) { }

    public record ActiveInput(boolean ativo) { }

    // ---------- agenda ----------
    public record WindowInput(@Min(1) @Max(7) int diaSemana, @NotNull LocalTime inicio, @NotNull LocalTime fim) { }

    public record BlockInput(Long profissionalId, @NotNull LocalDateTime inicio, @NotNull LocalDateTime fim, @Size(max = 200) String motivo) { }

    public record BlockView(Long id, Long profissionalId, String profissional, LocalDateTime inicio, LocalDateTime fim, String motivo) { }

    public record BlockResult(BlockView bloqueio, int agendamentosAfetados) { }

    public record SlotGroup(Long profissionalId, String profissional, List<String> horarios) { }

    // ---------- agendamento do cliente ----------
    public record HoldRequest(@NotEmpty(message = "Selecione ao menos um serviço.") @Size(max = 3, message = "Escolha no máximo 3 serviços.") List<Long> servicoIds,
                              @NotNull(message = "Selecione o profissional.") Long profissionalId,
                              @NotNull(message = "Selecione data e horário.") LocalDateTime inicio) { }

    public record HoldResponse(Long reservaId, String token, LocalDateTime inicio, LocalDateTime fim, LocalDateTime expiraEm,
                               int duracaoMinutos, BigDecimal precoServicos) { }

    public record ProductView(Long id, String nome, String marca, BigDecimal preco, boolean disponivel, boolean ilustrativo) { }

    public record ProductOrder(@NotNull Long produtoId, @Min(1) @Max(5) int quantidade) { }

    public record SubmitRequest(@NotNull Long reservaId, @NotBlank String token,
                                @NotBlank(message = "Informe seu nome.") @Size(max = 50) String nome,
                                @NotBlank(message = "Informe seu sobrenome.") @Size(max = 50) String sobrenome,
                                @NotBlank(message = "Informe seu e-mail.") @Email(message = "E-mail inválido.") @Size(max = 160) String email,
                                @Size(max = 30) String telefone, @Size(max = 300, message = "Alergias: no máximo 300 caracteres.") String alergias,
                                @Size(max = 500) String observacao, @Size(max = 4, message = "Máximo de 4 produtos.") List<@jakarta.validation.Valid ProductOrder> produtos,
                                @AssertTrue(message = "É necessário concordar com o uso dos dados para tratar o agendamento.") boolean aceitePrivacidade) { }

    public record ReleaseRequest(@NotNull Long reservaId, @NotBlank String token) { }

    public record BookingResult(Long id, String status, String mensagem) { }

    public record EmailRequest(@NotBlank @Email @Size(max = 160) String email) { }

    public record TokenRequest(@NotBlank String token) { }

    public record CustomerBooking(Long id, String servico, String profissional, LocalDateTime inicio, LocalDateTime fim,
                                  String status, String descricaoStatus, boolean podeCancelar, BigDecimal precoReferencia,
                                  String produtos, String origem) { }

    // ---------- administração ----------
    public record AdminBooking(Long id, String servico, String profissional, LocalDateTime inicio, LocalDateTime fim, String status,
                               String clienteNome, String clienteEmail, String clienteTelefone, String observacao,
                               LocalDateTime criadoEm, String canceladoPor, String origem) { }

    public record StatusInput(@NotBlank String status) { }

    public record AdminUserView(Long id, String username, String nome, String papel, boolean ativo,
                               LocalDateTime ultimoLoginEm, boolean exigeTrocaSenha) { }

    public record ResetPasswordInput(@NotBlank @Size(min = 12, max = 100, message = "A senha deve ter de 12 a 100 caracteres.") String novaSenha) { }

    public record AdminUserInput(@NotBlank @Size(min = 3, max = 60) String username, @NotBlank @Size(max = 100) String nome,
                                 @NotBlank @Size(min = 12, max = 100, message = "A senha deve ter de 12 a 100 caracteres.") String senhaTemporaria,
                                 @jakarta.validation.constraints.Pattern(regexp = "TECNICO|RECEPCAO", message = "Perfil inválido.") String papel) { }

    /** Agendamento feito no balcão por recepção ou proprietário (cliente pode não ter e-mail). */
    public record CounterBookingRequest(@jakarta.validation.constraints.NotEmpty @Size(max = 3) List<Long> servicoIds, @NotNull Long profissionalId,
                                        @NotNull LocalDateTime inicio, @NotBlank @Size(max = 100) String nome,
                                        @Size(max = 160) @jakarta.validation.constraints.Email String email, @Size(max = 30) String telefone,
                                        @Size(max = 300) String alergias, @Size(max = 500) String observacao,
                                        @Size(max = 4) List<ProductOrder> produtos) { }

    public record StockInput(@jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(100000) int quantidade, @Size(max = 100) String motivo) { }

    public record ProductInput(@NotBlank @Size(max = 100) String nome, @Size(max = 60) String marca,
                               @NotNull @jakarta.validation.constraints.DecimalMin("0.00") BigDecimal preco,
                               @jakarta.validation.constraints.Min(0) int estoque, @jakarta.validation.constraints.Min(0) int estoqueMinimo) { }

    public record PasswordChange(@NotBlank String senhaAtual,
                                 @NotBlank @Size(min = 12, max = 100, message = "A nova senha deve ter de 12 a 100 caracteres.") String novaSenha) { }
}
