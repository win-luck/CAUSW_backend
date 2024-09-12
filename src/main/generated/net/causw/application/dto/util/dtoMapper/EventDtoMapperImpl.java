package net.causw.application.dto.util.dtoMapper;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import net.causw.adapter.persistence.event.Event;
import net.causw.application.dto.event.EventResponseDto;
import net.causw.application.dto.event.EventResponseDto.EventResponseDtoBuilder;
import net.causw.application.dto.event.EventsResponseDto;
import net.causw.application.dto.event.EventsResponseDto.EventsResponseDtoBuilder;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2024-09-13T04:50:36+0900",
    comments = "version: 1.4.2.Final, compiler: javac, environment: Java 17.0.12 (Azul Systems, Inc.)"
)
@Component
public class EventDtoMapperImpl implements EventDtoMapper {

    @Override
    public EventResponseDto toEventResponseDto(Event event) {
        if ( event == null ) {
            return null;
        }

        EventResponseDtoBuilder eventResponseDto = EventResponseDto.builder();

        eventResponseDto.createdAt( formatDateTime( event.getCreatedAt() ) );
        eventResponseDto.updatedAt( formatDateTime( event.getUpdatedAt() ) );
        eventResponseDto.image( mapUuidFileToFileUrl( event.getEventImageUuidFile() ) );
        eventResponseDto.id( event.getId() );
        eventResponseDto.url( event.getUrl() );
        eventResponseDto.isDeleted( event.getIsDeleted() );

        return eventResponseDto.build();
    }

    @Override
    public EventsResponseDto toEventsResponseDto(Integer count, List<EventResponseDto> events) {
        if ( count == null && events == null ) {
            return null;
        }

        EventsResponseDtoBuilder eventsResponseDto = EventsResponseDto.builder();

        if ( count != null ) {
            eventsResponseDto.count( count );
        }
        if ( events != null ) {
            List<EventResponseDto> list = events;
            if ( list != null ) {
                eventsResponseDto.events( new ArrayList<EventResponseDto>( list ) );
            }
        }

        return eventsResponseDto.build();
    }
}
