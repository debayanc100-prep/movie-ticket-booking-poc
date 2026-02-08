package com.jts.movie.services;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.jts.movie.convertor.TicketConvertor;
import com.jts.movie.entities.Show;
import com.jts.movie.entities.ShowSeat;
import com.jts.movie.entities.Ticket;
import com.jts.movie.entities.User;
import com.jts.movie.exceptions.SeatsNotAvailable;
import com.jts.movie.exceptions.ShowDoesNotExists;
import com.jts.movie.exceptions.UserDoesNotExists;
import com.jts.movie.repositories.ShowRepository;
import com.jts.movie.repositories.TicketRepository;
import com.jts.movie.repositories.UserRepository;
import com.jts.movie.request.TicketRequest;
import com.jts.movie.response.TicketResponse;

@Service
public class TicketService {

	@Autowired
	private TicketRepository ticketRepository;

	@Autowired
	private ShowRepository showRepository;

	@Autowired
	private UserRepository userRepository;

	public TicketResponse ticketBooking(TicketRequest ticketRequest) {
		Optional<Show> showOpt = showRepository.findById(ticketRequest.getShowId());

		if (showOpt.isEmpty()) {
			throw new ShowDoesNotExists();
		}

		Optional<User> userOpt = userRepository.findById(ticketRequest.getUserId());

		if (userOpt.isEmpty()) {
			throw new UserDoesNotExists();
		}

		User user = userOpt.get();
		Show show = showOpt.get();

		Boolean isSeatAvailable = isSeatAvailable(show.getShowSeatList(), ticketRequest.getRequestSeats());

		if (!isSeatAvailable) {
			throw new SeatsNotAvailable();
		}

		// count price
		Integer getPriceAndAssignSeats = getPriceAndAssignSeats(show.getShowSeatList(),	ticketRequest.getRequestSeats());

		String seats = listToString(ticketRequest.getRequestSeats());

		Ticket ticket = new Ticket();
		ticket.setTotalTicketsPrice(getPriceAndAssignSeats);
		ticket.setBookedSeats(seats);
		ticket.setUser(user);
		ticket.setShow(show);

		ticket = ticketRepository.save(ticket);

		user.getTicketList().add(ticket);
		show.getTicketList().add(ticket);
		userRepository.save(user);
		showRepository.save(show);

		return TicketConvertor.returnTicket(show, ticket);
	}

	private Boolean isSeatAvailable(List<ShowSeat> showSeatList, List<String> requestSeats) {
		for (ShowSeat showSeat : showSeatList) {
			String seatNo = showSeat.getSeatNo();

			if (requestSeats.contains(seatNo) && !showSeat.getIsAvailable()) {
				return false;
			}
		}

		return true;
	}

	private Integer getPriceAndAssignSeats(List<ShowSeat> showSeatList, List<String> requestSeats) {
		Integer totalAmount = 0;

		for (ShowSeat showSeat : showSeatList) {
			if (requestSeats.contains(showSeat.getSeatNo())) {
				totalAmount += showSeat.getPrice();
				showSeat.setIsAvailable(Boolean.FALSE);
			}
		}

		return totalAmount;
	}
	
	private Integer calculateFinalPriceAndAssignSeats(Show show, List<String> requestedSeats) {
		int totalAmount = 0;
		int seatCount = 0;

		for (ShowSeat seat : show.getShowSeatList()) {
			if (requestedSeats.contains(seat.getSeatNo())) {
				totalAmount += seat.getPrice();
				seatCount++;
				seat.setIsAvailable(Boolean.FALSE);
			}
		}

		// Apply discounts
		totalAmount = applyDiscounts(show, totalAmount, seatCount);

		return totalAmount;
	}
	
	private Integer applyDiscounts(Show show, int totalAmount, int seatCount) {

	    int discountAmount = 0;

	    // ✅ Rule 1: 50% discount on 3rd ticket
	    if (seatCount >= 3 && isOfferApplicable(show)) {
	        int thirdTicketPrice = getThirdTicketPrice(show);
	        discountAmount += thirdTicketPrice / 2;
	    }

	    // ✅ Rule 2: 20% discount for afternoon show
	    if (show.getShowId() == 2 && isOfferApplicable(show)) {
	        discountAmount += (int) (totalAmount * 0.20);
	    }

	    return totalAmount - discountAmount;
	}

	private boolean isOfferApplicable(Show show) {

	    List<String> offerCities = List.of("BANGALORE", "MUMBAI", "DELHI");
	    List<String> offerTheatres = List.of("PVR", "INOX");

	    return offerCities.contains(show.getTheater())
	            && offerTheatres.contains(show.getTheater().getName().toUpperCase());
	}
	
	private int getThirdTicketPrice(Show show) {
	    return show.getShowSeatList().stream()
	            .filter(seat -> !seat.getIsAvailable())
	            .mapToInt(ShowSeat::getPrice)
	            .sorted()
	            .skip(2)
	            .findFirst()
	            .orElse(0);
	}



	private String listToString(List<String> requestSeats) {
		StringBuilder sb = new StringBuilder();

		for (String s : requestSeats) {
			sb.append(s).append(",");
		}

		return sb.toString();
	}

}
