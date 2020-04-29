package akkapi.cluster

import scala.language.implicitConversions

package object sudoku {

  type Seq[+A] = scala.collection.immutable.Seq[A]
  val Seq = scala.collection.immutable.Seq

  private val N = 9
  val CELLPossibleValues: Vector[Int] = (1 to N).toVector
  val cellIndexesVector: Vector[Int] = (0 until N).toVector // TODO - Refactor using Vector.range
  val initialCell: Set[Int] = Set(1 to N: _*)

  type CellContent = Set[Int]
  type ReductionSet = Vector[CellContent]
  type Sudoku = Vector[ReductionSet]

  opaque type CellUpdates = Vector[(Int, Set[Int])]
  object CellUpdates {
    def single(update: (Int, Set[Int])): CellUpdates = update +: empty
    val empty: CellUpdates = Vector.empty[(Int, Set[Int])]
  }
  extension on (updates: CellUpdates) {
    def size: Int = updates.size
    def toMap: Map[Int, Set[Int]] = updates.to(Map).withDefaultValue(Set(0))

    // Using 'Any' because cannot have type parameter on extension method
    def foreach(fn: (Int, Set[Int]) => Any): Unit = updates.foreach(fn.tupled)

    // Because we cannot have type parameters on extension methods, we cannot define a generic 'foldLeft' primitive
    // So we implement a specialised method for applying a set of updates to a ReductionSet
    def applyTo(reductionSet: ReductionSet): ReductionSet = (updates foldLeft reductionSet) {
      case (stateTally, (index, updatedCellContent)) =>
        stateTally.updated(index, stateTally(index) & updatedCellContent)
    }
  }

  extension {
    def (update: (Int, Set[Int])) +: (updates: CellUpdates): CellUpdates = update +: updates
  }

  given Eql[CellUpdates, CellUpdates] = Eql.derived

  val cellUpdatesEmpty = Vector.empty[(Int, Set[Int])]

  import SudokuDetailProcessor.RowUpdate

  implicit class RowUpdatesToSudokuField(val update: Vector[SudokuDetailProcessor.RowUpdate]) extends AnyVal {
    def toSudokuField: SudokuField = {
      val rows =
        update
          .map { case SudokuDetailProcessor.RowUpdate(id, cellUpdates) => (id, cellUpdates)}
        .to(Map).withDefaultValue(cellUpdatesEmpty)
      val sudoku = for {
        (row, cellUpdates) <- Vector.range(0, 9).map(row => (row, rows(row)))

        // We shouldn't need to use the 'toMap' extension method here because CellUpdates is not opaque in this file
        // But for some reason the 'to(Map)' construct doesn't work. It might be a problem of the combination of
        // an implicit conversion and the opaque type
        x = cellUpdates.toMap
        y = Vector.range(0, 9).map(n => x(n))
        } yield y
      SudokuField(sudoku)
    }
  }

  implicit class SudokuFieldOps(val sudokuField: SudokuField) extends AnyVal {
    def transpose: SudokuField = SudokuField(sudokuField.sudoku.transpose)

    def rotateCW: SudokuField = SudokuField(sudokuField.sudoku.reverse.transpose)

    def rotateCCW: SudokuField = SudokuField(sudokuField.sudoku.transpose.reverse)

    def flipVertically: SudokuField = SudokuField(sudokuField.sudoku.reverse)

    def flipHorizontally: SudokuField = sudokuField.rotateCW.flipVertically.rotateCCW

    def rowSwap(row1: Int, row2: Int): SudokuField = {
      SudokuField(
        sudokuField.sudoku.zipWithIndex.map {
        case (_, `row1`) => sudokuField.sudoku(row2)
        case (_, `row2`) => sudokuField.sudoku(row1)
        case (row, _) => row
        }
      )
    }

    def columnSwap(col1: Int, col2: Int): SudokuField = {
      sudokuField.rotateCW.rowSwap(col1, col2).rotateCCW
    }

    def randomSwapAround: SudokuField = {
      import scala.language.implicitConversions
      val possibleCellValues = Vector(1,2,3,4,5,6,7,8,9)
      // Generate a random swapping of cell values. A value 0 is used as a marker for a cell
      // with an unknown value (i.e. it can still hold all values 0 through 9). As such
      // a cell with value 0 should remain 0 which is why we add an entry to the generated
      // Map to that effect
      val shuffledValuesMap =
        possibleCellValues.zip(scala.util.Random.shuffle(possibleCellValues)).to(Map) + (0 -> 0)
      SudokuField(sudokuField.sudoku.map { row =>
        row.map(cell => Set(shuffledValuesMap(cell.head)))
      })
    }

    def toRowUpdates: Vector[RowUpdate] = {
      sudokuField
        .sudoku
        .map(_.zipWithIndex)
        .map(row => row.filterNot(_._1 == Set(0)))
        .zipWithIndex.filter(_._1.nonEmpty)
        .map { case (c, i) =>
          RowUpdate(i, c.map(_.swap))
        }
    }
  }
}