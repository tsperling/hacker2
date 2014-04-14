;A* Pathfinder (Version 1.7a) by Patrick Lester. Used by permission.
;==================================================================
;Last updated 03/02/03 - just changed a few variable names and comments to make it easier to understand

;An article describing A* and this code in particular can be found at:
;http://www.policyalmanac.org/games/aStarTutorial.htm

;If you want to use this AStar Library, you may do so free of charge so 
;long as the author byline (above) is retained. Thank you to CaseyC 
;at the Blitz Forums for suggesting the use of binary heaps for the open 
;list. Email comments and questions to Patrick Lester at 
;pwlester@policyalmanac.org.

;Setup
;-----
;1. Include "includes/aStarLibrary.bb" at the top of your program.

;2. Create an array called walkability(x,y) that contains information
;	about the walkability of each square/tile on your map, with
;	0 = walkable (the default value) and 1 = unwalkable. The array
;	should range from (0,0) in the upper left hand corner to 
;	(mapWidth-1,mapHeight-1) in the bottom right hand corner.

;3. Adjust the following variables at the top of the .declareVariables
;	subroutine below. TileSize, mapWidth and mapHeight should be
;	made global variables.
;	- numberPeople = however many monsters, characters and other
;		pathfinders you have that will be accessing the pathfinding
;		function. If this number is unknown, set it to a really high
;		number (maybe a 1000) that you are sure will not be exceeded.
;	- tileSize = the width and height of your square tiles in pixels
;	- mapWidth = the width of your map in tiles = x value in
;		walkability array.
;	- mapHeight = the height of your map in tiles = y value in
;		walkability array.

;4.	(Optional) If you want to preprocess the map for faster pathfinding,
;	use the following command after your map is loaded/generated
;
;		preProcessMap(mapWidth,mapHeight)
;	
;	with mapWidth being the width of your map in tiles/squares and
;	mapHeight being the height (as above).


;Calling the functions
;---------------------
;There are three main functions: findPath(), readPath() and preProcessMap().

;1.	findPath(pathfinderID,startingX,startingY,targetX,targetY)
;	- pathfinderID = unique number associated with this character or
;		monster (i.e., baddie(1), baddie(2) could be 1 and 2 respectively).
;	- startingX,startingY = location of the pathfinder (pixel based coordinates)
;	- targetX,targetY = location of the target destination (pixel based coordinates)

;	The findPath() function returns whether a path could be found (1) or
;	if it's nonexistent (2). If there is a path, it stores it in a bank
;	called pathBank(pathfinderID).

;2.	readPath(pathfinderID,currentX,currentY,pixelsPerFrame)
; 	This function reads the path data generated by findPath() and returns
;	the x and y coordinates of the next step on the path. They are stored
;	as xPath(pathfinderID) and yPath(pathfinderID). These coordinates are
;	pixel coordinates on the screen. See the function for more info.
;	The parameters are:
;	- pathfinderID = same as above
;	- currentX,currentY = current location of pathfinder in pixel 
;		based coordinates
;	- pixelsPerFrame = number of pixels traveled per frame. This info
;		is used by the path finder to determine how close to the center
;		of a square the pathFinder must get before it looks up the 
;		next step on the path.

;3.	preProcessMap(mapWidth,mapHeight) = this function preprocesses 
;	the map for faster pathfinding.


;==========================================================
;DECLARE VARIABLES
.declareVariables

;	Preset # variables needing to be preset (see notes under "setup"
;	above). In this case, since this library is being used by several different
;	demos with different stats, we actually declare mapWidth, mapHeight, and
;	tileSize in those demos.
	Global mapWidth = 38, mapHeight = 24, tileSize = 1
	numberPeople = 1

;	Create needed arrays
	Dim openList(mapWidth*mapHeight+2) ;1 dimensional array holding ID# of open list items
	Dim whichList(mapWidth+1,mapHeight+1)  ;2 dimensional array used to record 
; 		whether a cell is on the open list or on the closed list.
	Dim openX(mapWidth*mapHeight+2) ;1d array stores the x location of an item on the open list
	Dim openY(mapWidth*mapHeight+2) ;1d array stores the y location of an item on the open list
	Dim parentX(mapWidth+1,mapHeight+1) ;2d array to store parent of each cell (x)
	Dim parentY(mapWidth+1,mapHeight+1) ;2d array to store parent of each cell (y)
	Dim Fcost(mapWidth*mapHeight+2)	;1d array to store F cost of a cell on the open list
	Dim Gcost(mapWidth+1,mapHeight+1) 	;2d array to store G cost for each cell.
	Dim Hcost(mapWidth*mapHeight+2)	;1d array to store H cost of a cell on the open list
	Dim pathLength(numberPeople+1)      ;stores length of the found path for critter
	Dim pathLocation(numberPeople+1)    ;stores current position along the chosen path for critter		

;	Create an arrayed bank called pathBank to save path data to.
;	Note: Blitzers who use types for their units can replace this method with a bank
;	saved in the type. 
	Dim pathBank(numberPeople+1)
	For x = 0 To numberPeople+1	
		pathBank(x) = CreateBank(1)
	Next
	
;	Declare constants
	Global onClosedList = 10
	Const notfinished = 0, notStarted = 0; path-related constants
	Const found = 1, nonexistent = 2 
	Const walkable = -1, unwalkable = -16777216; walkability array constants

;	Declare path reading variables
	Dim pathStatus(numberPeople+1)
	Dim xPath(numberPeople+1)
	Dim yPath(numberPeople+1) 
	
;	Map Preprocessing Stuff
	Dim tempWalkability(mapWidth+2,mapHeight+2)
	Dim area(mapWidth+1,mapHeight+1) ;area that a tile is assigned to
	Dim island(mapWidth+1,mapHeight+1) ;island that a tile is assigned to		
	Const deadEnd = 10
	Dim adjacentArea(10)

;==========================================================
;FIND PATH: This function finds the path and saves it.

.findPathFunction
Function findPath(pathfinderID,startingX,startingY,targetX,targetY)


;1.	Convert location data (in pixels) to coordinates in the walkability array.
	startX = startingX/tileSize : startY = startingY/tileSize	
	targetX = targetX/tileSize : targetY = targetY/tileSize


;2.	Quick Path Checks: Under the some circumstances no path needs to
;	be generated ...

;	If starting location and target are in the same location...
	If startX = targetX And startY = targetY And pathLocation(pathfinderID) > 0 Then Return found
	If startX = targetX And startY = targetY And pathLocation(pathfinderID) = 0 Then Return nonexistent

;	If target square is unwalkable, return that it's a nonexistent path.
	If walkability(targetX,targetY) = unwalkable Then Goto noPath

;	If target square is not on the same "island," return that it's a 
;	nonexistent path. Note that this check only works if the map has been
;	preprocessed.
	If island(startX,startY) <> island(targetX,targetY) Then Goto noPath


;3.	Reset some variables that need to be cleared
	If onClosedList > 1000000 ;reset whichList occasionally
		Dim whichList(mapWidth,mapHeight) : onClosedList = 10
	End If	
	onClosedList = onClosedList+2 ;changing the values of onOpenList and onClosed list is faster than redimming whichList() array
	onOpenList = onClosedList-1
	pathLength(pathfinderID) = notstarted ;i.e, = 0
	pathLocation(pathfinderID) = notstarted ;i.e, = 0
	Gcost(startX,startY) = 0 ;reset starting square's G value to 0


;4.	Add the starting location to the open list of squares to be checked.
	numberOfOpenListItems = 1
	openList(1) = 1 ;assign it as the top (and currently only) item in the open list, which is maintained as a binary heap (explained below)
	openX(1) = startX : openY(1) = startY


;5.	Do the following until a path is found or deemed nonexistent.
	Repeat

	
;6.	If the open list is not empty, take the first cell off of the list.
;	This is the lowest F cost cell on the open list.
	If numberOfOpenListItems <> 0 Then

;	Pop the first item off the open list.
	parentXval = openX(openList(1)) : parentYVal = openY(openList(1)) ;record cell coordinates of the item
	whichList(parentXval,parentYVal) = onClosedList ;add the item to the closed list

;	Open List = Binary Heap: Delete this item from the open list, which
;	is maintained as a binary heap. For more information on binary heaps, see:
;		http://www.policyalmanac.org/games/binaryHeaps.htm
	numberOfOpenListItems = numberOfOpenListItems - 1 ;reduce number of open list items by 1	
		
;	Delete the top item in binary heap and reorder the heap, with the lowest F cost item rising to the top.
	openList(1) = openList(numberOfOpenListItems+1) ;move the last item in the heap up to slot #1
	v = 1

;	Repeat the following until the new item in slot #1 sinks to its proper spot in the heap.
	Repeat
	u = v
		
	If 2*u+1 <= numberOfOpenListItems ;if both children exist
	 	;Check if the F cost of the parent is greater than each child.
		;Select the lowest of the two children.	
		If Fcost(openList(u)) >= Fcost(openList(2*u)) Then v = 2*u
		If Fcost(openList(v)) >= Fcost(openList(2*u+1)) Then v = 2*u+1		
	Else
	If 2*u <= numberOfOpenListItems ;if only child #1 exists
	 	;Check if the F cost of the parent is greater than child #1	
		If Fcost(openList(u)) >= Fcost(openList(2*u)) Then v = 2*u
	End If	
	End If

	If u<>v ;if parent's F is > one of its children, swap them
		temp = openList(u)
		openList(u) = openList(v)
		openList(v) = temp				
	Else
		Exit ;otherwise, exit loop
	End If	
	Until KeyDown(1)

	
;7.	Check the adjacent squares. (Its "children" -- these path children
;	are similar, conceptually, to the binary heap children mentioned
;	above, but don't confuse them. They are different. Path children
;	are portrayed in Demo 1 with grey pointers pointing toward
;	their parents.) Add these adjacent child squares to the open list
;	for later consideration if appropriate (see various if statements
;	below).
	For b = parentYVal-1 To parentYVal+1
	For a = parentXval-1 To parentXval+1

;	If not off the map (do this first to avoid array out-of-bounds errors)
	If a <> -1 And b <> -1 And a <> mapWidth And b <> mapHeight

;	If not already on the closed list (items on the closed list have
;	already been considered and can now be ignored).			
	If whichList(a,b) <> onClosedList 
	
;	If not a wall/obstacle square.
	If walkability(a,b) <> unwalkable 
		
;	If not a deadend, unless in the starting or target area (This if 
;	statement can be dropped if you are not using the map preprocessor.
;	Don't forget to also drop the corresponding End If statement at the 
;	bottom of section #8).
	If walkability(a,b) <> deadend Or area(a,b) = area(startX,startY) Or area(a,b) = area(targetX,targetY)
	
;	Don't cut across corners
	corner = walkable	
	If a = parentXVal-1 
		If b = parentYVal-1 
			If walkability(parentXval-1,parentYval) = unwalkable Or walkability(parentXval,parentYval-1) = unwalkable Then corner = unwalkable
		Else If b = parentYVal+1 
			If walkability(parentXval,parentYval+1) = unwalkable Or walkability(parentXval-1,parentYval) = unwalkable Then corner = unwalkable 
		End If
	Else If a = parentXVal+1 
		If b = parentYVal-1 
			If walkability(parentXval,parentYval-1) = unwalkable Or walkability(parentXval+1,parentYval) = unwalkable Then corner = unwalkable 
		Else If b = parentYVal+1 
			If walkability(parentXval+1,parentYval) = unwalkable Or walkability(parentXval,parentYval+1) = unwalkable Then corner = unwalkable 
		End If
	End If			
	If corner = walkable
	
;	If not already on the open list, add it to the open list.			
	If whichList(a,b) <> onOpenList	

;		Create a new open list item in the binary heap.
		newOpenListItemID = newOpenListItemID + 1; each new item has a unique ID #
		m = numberOfOpenListItems+1
		openList(m) = newOpenListItemID	 ;place the new open list item (actually, its ID#) at the bottom of the heap
		openX(newOpenListItemID) = a : openY(newOpenListItemID) = b ;record the x and y coordinates of the new item

;		Figure out its G cost
		If Abs(a-parentXval) = 1 And Abs(b-parentYVal) = 1 Then
			addedGCost = 14 ;cost of going to diagonal squares	
		Else	
			addedGCost = 10 ;cost of going to non-diagonal squares				
		End If
		Gcost(a,b) = Gcost(parentXval,parentYVal)+addedGCost
			
;		Figure out its H and F costs and parent

;		Hcost(openList(m)) = estimateHcost(a,b,targetx,targety)	;this
;			line is commented out because calling the estimateHcost
;			function is a bit slower than just including the equation
;			here. But you might want to uncomment and use that
;			line if you want to use one of the alternative H estimation
;			methods included in the estimateHcost function. If you
;			have no idea what this is all about, you can just leave
;			it alone.

		Hcost(openList(m)) = 10*(Abs(a - targetx) + Abs(b - targety)) ; record the H cost of the new square
		Fcost(openList(m)) = Gcost(a,b) + Hcost(openList(m)) ;record the F cost of the new square
		parentX(a,b) = parentXval : parentY(a,b) = parentYVal	;record the parent of the new square	
		
;		Move the new open list item to the proper place in the binary heap.
;		Starting at the bottom, successively compare to parent items,
;		swapping as needed until the item finds its place in the heap
;		or bubbles all the way to the top (if it has the lowest F cost).
		While m <> 1 ;While item hasn't bubbled to the top (m=1)	
			;Check if child's F cost is < parent's F cost. If so, swap them.	
			If Fcost(openList(m)) <= Fcost(openList(m/2)) Then
				temp = openList(m/2)
				openList(m/2) = openList(m)
				openList(m) = temp
				m = m/2
			Else
				Exit
			End If
		Wend 
		numberOfOpenListItems = numberOfOpenListItems+1 ;add one to the number of items in the heap

;		Change whichList to show that the new item is on the open list.
		whichList(a,b) = onOpenList


;8.	If adjacent cell is already on the open list, check to see if this 
;	path to that cell from the starting location is a better one. 
;	If so, change the parent of the cell and its G and F costs.	
	Else; If whichList(a,b) = onOpenList
	
;		Figure out the G cost of this possible new path
		If Abs(a-parentXval) = 1 And Abs(b-parentYVal) = 1 Then
			addedGCost = 14;cost of going to diagonal tiles	
		Else	
			addedGCost = 10 ;cost of going to non-diagonal tiles				
		End If
		tempGcost = Gcost(parentXval,parentYVal)+addedGCost
		
;		If this path is shorter (G cost is lower) then change
;		the parent cell, G cost and F cost. 		
		If tempGcost < Gcost(a,b) Then 	;if G cost is less,
			parentX(a,b) = parentXval 	;change the square's parent
			parentY(a,b) = parentYVal
			Gcost(a,b) = tempGcost 	;change the G cost			

;			Because changing the G cost also changes the F cost, if
;			the item is on the open list we need to change the item's
;			recorded F cost and its position on the open list to make
;			sure that we maintain a properly ordered open list.
			For x = 1 To numberOfOpenListItems ;look for the item in the heap
			If openX(openList(x)) = a And openY(openList(x)) = b Then ;item found
				FCost(openList(x)) = Gcost(a,b) + HCost(openList(x)) ;change the F cost
				
;				See if changing the F score bubbles the item up from it's current location in the heap
				m = x
				While m <> 1 ;While item hasn't bubbled to the top (m=1)	
					;Check if child is < parent. If so, swap them.	
					If Fcost(openList(m)) < Fcost(openList(m/2)) Then
						temp = openList(m/2)
						openList(m/2) = openList(m)
						openList(m) = temp
						m = m/2
					Else
						Exit ;while/wend
					End If
				Wend 
				
				Exit ;for x = loop
			End If ;If openX(openList(x)) = a
			Next ;For x = 1 To numberOfOpenListItems

		End If ;If tempGcost < Gcost(a,b) Then			

	End If ;If not already on the open list				
	End If ;If corner = walkable
	End If ;If walkability(a,b) <> deadend
	End If ;If not a wall/obstacle cell.	
	End If ;If not already on the closed list	
	End If ;If not off the map.	
	Next
	Next


;9.	If open list is empty then there is no path.	
	Else
		path = nonExistent : Exit
	End If

;	If target is added to open list then path has been found.
	If whichList(targetx,targety) = onOpenList Then path = found : Exit		

	Until KeyDown(1) ;repeat until path is found or deemed nonexistent
	

;10.	Save the path if it exists. Copy it to an arrayed bank. 
;	Arrayed banks give you the best of both arrays and types. Like an
;	array, they are pointable. Like a type, you can resize it as 
;	needed without losing all of your data (which is what happens when 
;	you redim an array).
	If path = found Then 
		
;a.	Working backwards from the target to the starting location by checking
;	each cell's parent, figure out the length of the path.
	pathX = targetX : pathY = targetY	
	Repeat

;	Look up the parent of the current cell.	
	tempx = parentX(pathX,pathY)		
	pathY = parentY(pathX,pathY)
	pathX = tempx

;	Figure out the path length
	pathLength(pathfinderID) = pathLength(pathfinderID) + 1
	
	Until pathX = startX And pathY = startY
	
;b.	Resize the data bank to the right size (include a 15 byte buffer
;	on both sides of data to ensure no memory overwrites)	
	ResizeBank pathBank(pathfinderID),pathLength(pathfinderID)*4+30

;c.	Now copy the path information over to the databank. Since we are
;	working backwards from the target to the start location, we copy
;	the information to the data bank in reverse order. The result is
;	a properly ordered set of path data, from the first step to the
;	last.
	pathX = targetX : pathY = targetY	
	cellPosition = pathlength(pathfinderID)*4+10 ;start at the end	
	Repeat
	DebugLog "Path = " + pathX  + " " + pathY 	
	cellPosition = cellPosition - 4 ;work backwards
	PokeShort pathBank(pathfinderID),cellPosition,pathX    ;store x value	
	PokeShort pathBank(pathfinderID),cellPosition+2,pathY  ;store y value
	
;d.	Look up the parent of the current cell.	
	tempx = parentX(pathX,pathY)		
	pathY = parentY(pathX,pathY)
	pathX = tempx	

;e.	If we have reached the starting square, exit the loop.	
	Until pathX = startX And pathY = startY	


;11.Read the first path step into xPath/yPath arrays
	readPath(pathfinderID,startingX,startingY,1)
	End If ;If path = found Then 


;12.Return info on whether a path has been found.
	Return path; Returns 1 if a path has been found, 2 if no path exists.
 

;13.If there is no path to the selected target, set the pathfinder's
;	xPath and yPath equal to its current location and return that the
;	path is nonexistent.
	.noPath
	xPath(pathfinderID) = startingX
	yPath(pathfinderID) = startingY
	Return nonexistent

	End Function
	
	
Function estimateHcost(a,b,targetx,targety)
;	This function estimates the distance from a given square to the
;	target square, usually refered to as H or the H cost.

;		(1) Manhattan (dx+dy)	
		H = 10*(Abs(a - targetx) + Abs(b - targety))
			
	Return H

;		Other possible H estimate methods (not used here) include:
					
;		(1) Diagonal Shortcut Estimation Method
		xDistance = Abs(a - targetx)
		yDistance = Abs(b - targety)
		If xDistance > yDistance Then 
			H = 14*yDistance + 10*(xDistance-yDistance)
		Else
			H = 14*xDistance + 10*(yDistance-xDistance)
		End If	

;		(2) Max(dx,dy)
		xDistance = Abs(a - targetx)
		yDistance = Abs(b - targety)
		If xDistance > yDistance Then 
			H = 10*xDistance
		Else
			H = 10*yDistance
		End If

	End Function	


;==========================================================
;READ PATH DATA: These functions read the path data and convert
;it to screen pixel coordinates.
.readPathFunction
Function readPath(pathfinderID,currentX#,currentY#,pixelsPerFrame#)

;	Note on PixelsPerFrame: The need for this parameter probably isn't
;	that obvious, so a little explanation is in order. This
;	parameter is used to determine if the pathfinder has gotten close
;	enough to the center of a given path square to warrant looking up
;	the next step on the path.
;	 
;	This is needed because the speed of certain sprites can
;	make reaching the exact center of a path square impossible.
;	In Demo #2, the chaser has a velocity of 3 pixels per frame. Our
;	tile size is 50 pixels, so the center of a tile will be at location
;	25, 75, 125, etc. Some of these are not evenly divisible by 3, so
;	our pathfinder has to know how close is close enough to the center.
;	It calculates this by seeing if the pathfinder is less than 
;	pixelsPerFrame # of pixels from the center of the square. 

;	This could conceivably cause problems if you have a *really* fast
;	sprite and/or really small tiles, in which case you may need to
;	adjust the formula a bit. But this should almost never be a problem
;	for games with standard sized tiles and normal speeds. Our smiley
;	in Demo #4 moves at a pretty fast clip and it isn't even close
;	to being a problem.

	ID = pathfinderID ;redundant, but makes the following easier to read

;	If a path has been found for the pathfinder	...
	If pathStatus(ID) = found

;		If path finder is just starting a new path or has reached the 
;		center of the current path square (and the end of the path
;		hasn't been reached), look up the next path square.
		If pathLocation(ID) < pathLength(ID)
		If pathLocation(ID) = 0 Or (Abs(currentX# - xPath(ID)) < pixelsPerFrame# And Abs(currentY# - yPath(ID)) < pixelsPerFrame#) Then ;if close enough to center of square
			pathLocation(ID) = pathLocation(ID) + 1
		End If
		End If	

;		Read the path data.		
		xPath(ID) = readPathX(pathfinderID,pathLocation(ID))
		yPath(ID) = readPathY(pathfinderID,pathLocation(ID))

;		If the center of the last path square on the path has been 
;		reached then reset.
		If pathLocation(ID) = pathLength(ID) 
			If Abs(currentX# - xPath(ID)) < pixelsPerFrame# And Abs(currentY# - yPath(ID)) < pixelsPerFrame# Then ;if close enough to center of square
				pathStatus(ID) = notstarted 
			End If		
		End If

;	If there is no path for this pathfinder, simply stay in the current
; 	location.
	Else
	
		xPath(ID) = currentX#
		yPath(ID) = currentY#

	End If ;If pathStatus(ID) = found

End Function



;The following two functions read the raw path data from the pathBank.
;You can call these functions directly and skip the readPath function
;above if you want. Make sure you know what your current pathLocation
;is.
Function readPathX(pathfinderID,pathLocation)

	If pathLocation <= pathLength(pathfinderID) Then

;	Read coordinate from bank
	x = PeekShort (pathBank(pathfinderID),pathLocation*4+6)

;	Adjust the coordinates so they align with the center
;	of the path square (optional). This assumes that you are using
;	sprites that are centered -- i.e., with the midHandle command.
;	Otherwise you will want to adjust this.
	x = tileSize*x; + .5*tileSize
	
	End If

	Return x
	End Function	


Function readPathY(pathfinderID,pathLocation)

	If pathLocation <= pathLength(pathfinderID) Then

;	Read coordinate from bank
	y = PeekShort (pathBank(pathfinderID),pathLocation*4+8)

;	Adjust the coordinates so they align with the center
;	of the path square (optional). This assumes that you are using
;	sprites that are centered -- i.e., with the midHandle command.
;	Otherwise you will want to adjust this.
	y = tileSize*y; + .5*tileSize
	
	End If

	Return y
	End Function
	