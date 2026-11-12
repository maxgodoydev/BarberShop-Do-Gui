package br.com.barbershopdogui.service;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Envia e-mail FORA da transação e FORA da thread da requisição.
 * Antes, BookingService.submit / CustomerAccessService.requestLink / changeStatus chamavam mail.send() dentro de @Transactional:
 * cada envio lento segurava uma conexão do pool (5) e um FOR UPDATE; com SMTP bloqueado/lento o site inteiro travava.
 * Agora: dentro de transação, o envio só é enfileirado DEPOIS do commit (rollback = nada é enviado).
 * Fila limitada: se encher, descarta com aviso no log (preferível a derrubar o site).
 */
public class DeferredEmailSender implements EmailSender, DisposableBean {
    private static final Logger LOG = LoggerFactory.getLogger(DeferredEmailSender.class);

    private final EmailSender delegate;
    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(200), r -> {
        Thread t = new Thread(r, "mail-sender");
        t.setDaemon(true);
        return t;
    });

    public DeferredEmailSender(EmailSender delegate) {
        this.delegate = delegate;
    }

    @Override
    public void send(String to, String subject, String body) {
        Runnable task = () -> {
            try {
                delegate.send(to, subject, body);
            } catch (RuntimeException ex) {
                LOG.warn("Falha ao enviar e-mail ({}).", ex.getClass().getSimpleName()); // sem dados pessoais no log
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enqueue(task);
                }
            });
        } else {
            enqueue(task);
        }
    }

    private void enqueue(Runnable task) {
        try {
            pool.execute(task);
        } catch (RejectedExecutionException ex) {
            LOG.warn("Fila de e-mail cheia: mensagem descartada.");
        }
    }

    @Override
    public boolean isRealDelivery() {
        return delegate.isRealDelivery();
    }

    @Override
    public void destroy() {
        pool.shutdown();
    }
}
