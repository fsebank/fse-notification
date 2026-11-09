package com.fse.fsenotification.modules.message.repository;

import com.fse.fsenotification.modules.message.domain.MessagePrice;
import com.fse.fsenotification.modules.message.enums.MessageChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MessagePriceRepository extends JpaRepository<MessagePrice, Long> {

    Optional<MessagePrice> findByChannel(MessageChannel channel);

    boolean existsByChannel(MessageChannel channel);

    List<MessagePrice> findAllByOrderByChannelAsc();
}
