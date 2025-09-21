package com.rewabank.customer.command.Intercepter;

import com.rewabank.customer.Entity.Customer;
import com.rewabank.customer.command.CreateCustomerCommand;
import com.rewabank.customer.command.DeleteCustomerCommand;
import com.rewabank.customer.command.UpdateCustomerCommand;
import com.rewabank.customer.exception.CustomerAlreadyExistsException;
import com.rewabank.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.springframework.stereotype.Component;
import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerCommandInterceptor implements MessageDispatchInterceptor<CommandMessage<?>> {

    private final CustomerRepository customerRepository;

    @Nonnull
    @Override
    public BiFunction<Integer, CommandMessage<?>, CommandMessage<?>> handle(@Nonnull List<? extends CommandMessage<?>> messages) {
        return (index,command)->{
            if(CreateCustomerCommand.class.equals(command.getPayloadType())){
                CreateCustomerCommand createCustomerCommand= (CreateCustomerCommand) command.getPayload();

                Optional<Customer> customer = customerRepository.
                        findByMobileNumberAndActiveSw(createCustomerCommand.getMobileNumber(), true);
                if (customer.isPresent()) {
                    throw new CustomerAlreadyExistsException("Customer already exists with the given mobile number: " + createCustomerCommand.getMobileNumber());
                }
            } else if (UpdateCustomerCommand.class.equals(command.getPayloadType())){
                UpdateCustomerCommand updateCustomerCommand= (UpdateCustomerCommand) command.getPayload();

                Optional<Customer> customer = customerRepository.
                        findByMobileNumberAndActiveSw(updateCustomerCommand.getMobileNumber(), true);
                if (customer.isPresent()) {
                    throw new CustomerAlreadyExistsException("Customer already exists with the given mobile number: " + updateCustomerCommand.getMobileNumber());
                }
            }
            else if(DeleteCustomerCommand.class.equals(command.getPayloadType())){
                DeleteCustomerCommand deleteCustomerCommand= (DeleteCustomerCommand) command.getPayload();

                Optional<Customer> customer = customerRepository.
                        findByCustomerIdAndActiveSw(deleteCustomerCommand.getCustomerId(), true);
                if (customer.isPresent()) {
                    throw new CustomerAlreadyExistsException("Customer already exists with the given mobile number: " + deleteCustomerCommand.getCustomerId());
                }
            }
            return command;
        };
    }
}
